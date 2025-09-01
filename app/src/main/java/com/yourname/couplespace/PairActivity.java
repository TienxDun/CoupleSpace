package com.yourname.couplespace;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.*;

public class PairActivity extends AppCompatActivity {
    private TextView txtCode;
    private FirebaseFirestore db;
    private FirebaseUser me;
    private TextView txtState; private EditText edtCode;
    private ListenerRegistration coupleListener;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pair);
        setTitle("Ghép cặp");

        db = FirebaseFirestore.getInstance();
        me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }

        txtState = findViewById(R.id.txtState);
        edtCode  = findViewById(R.id.edtCode);
        txtCode = findViewById(R.id.txtCode);

        findViewById(R.id.btnCreate).setOnClickListener(v -> requireNoCoupleThen(this::createCouple));
        findViewById(R.id.btnJoin).setOnClickListener(v -> requireNoCoupleThen(this::joinCouple));

        loadState();
    }

    private void loadState() {
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(snap -> {
                    String coupleId = snap.getString("coupleId");
                    if (coupleId == null) {
                        txtState.setText("Chưa ghép cặp");
                        txtCode.setText("Mã cặp: (chưa tạo)");
                    } else {
                        txtState.setText("Đã ghép: " + coupleId);
                        // lấy code để hiển thị
                        db.collection("couples").document(coupleId).get()
                                .addOnSuccessListener(cpl -> {
                                    String code = cpl.getString("code");
                                    if (code != null) txtCode.setText("Mã cặp: " + code);
                                });
                    }
                });
    }

    private String randomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for (int i=0;i<6;i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private void createCouple() {
        String code = randomCode();
        String coupleId = db.collection("couples").document().getId();

        WriteBatch batch = db.batch();

        DocumentReference coupleRef = db.collection("couples").document(coupleId);
        Map<String, Object> couple = new HashMap<>();
        couple.put("code", code);
        couple.put("members", Arrays.asList(me.getUid()));
        couple.put("createdAt", FieldValue.serverTimestamp());
        batch.set(coupleRef, couple);

        DocumentReference codeRef = db.collection("codes").document(code);
        Map<String, Object> codeDoc = new HashMap<>();
        codeDoc.put("coupleId", coupleId);
        codeDoc.put("createdBy", me.getUid());
        codeDoc.put("createdAt", FieldValue.serverTimestamp());
        batch.set(codeRef, codeDoc);

        DocumentReference meRef = db.collection("users").document(me.getUid());
        batch.set(meRef, Collections.singletonMap("coupleId", coupleId), SetOptions.merge());

        batch.commit().addOnSuccessListener(v -> {
            txtCode.setText("Mã cặp: " + code);
            txtState.setText("Đợi người kia nhập mã...");
            watchCouple(coupleId); // ✅ auto chuyển Home khi đủ 2 người
        }).addOnFailureListener(e -> {
            Log.e("PairActivity", "Join failed", e);
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void joinCouple() {
        String code = edtCode.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (code.length()!=6) { Toast.makeText(this,"Mã 6 ký tự",Toast.LENGTH_SHORT).show(); return; }

        db.collection("codes").document(code).get().addOnSuccessListener(snap -> {
            if (!snap.exists()) { Toast.makeText(this,"Mã không đúng",Toast.LENGTH_SHORT).show(); return; }
            String coupleId = snap.getString("coupleId");

            db.runTransaction(tr -> {
                        DocumentReference coupleRef = db.collection("couples").document(coupleId);
                        DocumentSnapshot cpl = tr.get(coupleRef);

                        List<String> members = (List<String>) cpl.get("members");
                        if (members == null) members = new ArrayList<>();
                        if (members.contains(me.getUid())) return null;
                        if (members.size() >= 2)
                            throw new FirebaseFirestoreException("Cặp đã đủ 2 người", FirebaseFirestoreException.Code.ABORTED);

                        members.add(me.getUid());
                        tr.update(coupleRef, "members", members);

                        DocumentReference meRef = db.collection("users").document(me.getUid());
                        tr.set(meRef, Collections.singletonMap("coupleId", coupleId), SetOptions.merge());
                        return null;
                    }).addOnSuccessListener(v -> {
                        txtState.setText("Ghép thành công!");
                        goHome();  // chuyển Home ngay cho B
                    })

                    .addOnFailureListener(e -> {
                        Log.e("PairActivity", "Join failed", e);
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        });
    }
    private void watchCouple(String coupleId) {
        if (coupleListener != null) coupleListener.remove();
        coupleListener = db.collection("couples").document(coupleId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null || !snap.exists()) return;
                    List<String> members = (List<String>) snap.get("members");
                    if (members != null && members.size() >= 2) {
                        Toast.makeText(this, "Người kia đã ghép xong!", Toast.LENGTH_SHORT).show();
                        goHome();
                    }
                });
    }
    private void requireNoCoupleThen(Runnable action) {
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(snap -> {
                    if (snap.getString("coupleId") != null) {
                        Toast.makeText(this, "Bạn đã thuộc một cặp rồi.", Toast.LENGTH_SHORT).show();
                    } else {
                        action.run();
                    }
                });
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (coupleListener != null) {
            coupleListener.remove();
            coupleListener = null;
        }
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
