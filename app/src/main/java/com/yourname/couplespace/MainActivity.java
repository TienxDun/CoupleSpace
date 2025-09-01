package com.yourname.couplespace;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.FieldValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private ActivityResultLauncher<Intent> signInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();

        // 1) Cấu hình GoogleSignIn với ID token
        String webClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        GoogleSignInClient googleClient = GoogleSignIn.getClient(this, gso);

        // 2) Đăng ký nhận kết quả
        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            String idToken = account.getIdToken();
                            AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
                            auth.signInWithCredential(cred)
                                    .addOnSuccessListener(r -> {
                                        toast("Đăng nhập Google OK");
                                        ensureUserProfile();   // ✅ tạo/ghi users/{uid}
                                        refreshFcmToken();     // ✅ lưu fcmToken (dùng cho thông báo sau này)
                                        // TODO: nếu users/{uid}.coupleId != null -> chuyển sang HomeActivity
                                        goNext();  // <-- chuyển màn
                                    })
                                    .addOnFailureListener(e -> toast("Firebase auth lỗi: " + e.getMessage()));

                        } catch (ApiException e) {
                            toast("Google sign-in lỗi: " + e.getMessage());
                        }
                    }
                }
        );

        // 3) Nút đăng nhập
        findViewById(R.id.btnGoogle).setOnClickListener(v -> {
            Intent signIn = googleClient.getSignInIntent();
            signInLauncher.launch(signIn);
        });

        /*ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });*/
    }

    private void ensureUserProfile() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;

        Map<String, Object> doc = new HashMap<>();
        doc.put("displayName", u.getDisplayName());
        doc.put("email", u.getEmail());
        doc.put("photoUrl", (u.getPhotoUrl() != null ? u.getPhotoUrl().toString() : null));
        doc.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("users").document(u.getUid())
                .set(doc, SetOptions.merge()); // tạo mới hoặc cập nhật
    }

    private void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                    if (u == null) return;
                    FirebaseFirestore.getInstance()
                            .collection("users").document(u.getUid())
                            .set(Collections.singletonMap("fcmToken", token), SetOptions.merge());
                });
    }
    private void goNext() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                .addOnSuccessListener(snap -> {
                    String coupleId = snap.getString("coupleId");
                    if (coupleId == null) {
                        startActivity(new Intent(this, PairActivity.class));
                    } else {
                        startActivity(new Intent(this, HomeActivity.class));
                    }
                    finish();
                });
    }

    private void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}