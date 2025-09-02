package com.yourname.couplespace.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.yourname.couplespace.R;

import java.text.SimpleDateFormat;
import java.util.*;

public class MoodFragment extends Fragment {

    // Firebase
    private FirebaseFirestore db;
    private FirebaseUser me;

    // State
    private String coupleId;
    private String partnerUid;
    private ListenerRegistration partnerListener;

    // UI
    private RadioGroup emojiGroup;
    private EditText edtNote;
    private TextView txtPartnerMood, txtHistory, txtToday, txtPartnerName, txtMyMoodStatus;

    // State management
    private boolean isDataLoaded = false;
    private String currentNote;
    private String currentEmoji;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mood, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        
        // Initialize Firebase only if not already done
        if (db == null) {
            db = FirebaseFirestore.getInstance();
        }
        if (me == null) {
            me = FirebaseAuth.getInstance().getCurrentUser();
        }

        initViews(v);
        restoreState(s);

        // Only initialize data if not already loaded
        if (!isDataLoaded) {
            initializeData();
        }
    }

    private void initViews(@NonNull View v) {
        // Bind UI
        emojiGroup      = v.findViewById(R.id.emojiGroup);
        edtNote         = v.findViewById(R.id.edtNote);
        txtPartnerMood  = v.findViewById(R.id.txtPartnerMood);
        txtHistory      = v.findViewById(R.id.txtHistory);
        txtToday        = v.findViewById(R.id.txtToday);
        txtPartnerName  = v.findViewById(R.id.txtPartnerName);
        txtMyMoodStatus = v.findViewById(R.id.txtMyMoodStatus);

        // Header ngày
        if (txtToday != null) {
            txtToday.setText("Mood hôm nay (" + todayStr("dd/MM/yyyy") + ")");
        }

        v.findViewById(R.id.btnSaveMood).setOnClickListener(x -> saveMood());
        
        // Restore UI state if available
        if (currentNote != null && edtNote != null) {
            edtNote.setText(currentNote);
        }
        if (currentEmoji != null && emojiGroup != null) {
            restoreEmojiSelection(currentEmoji);
        }
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            isDataLoaded = savedInstanceState.getBoolean("isDataLoaded", false);
            coupleId = savedInstanceState.getString("coupleId");
            partnerUid = savedInstanceState.getString("partnerUid");
            currentNote = savedInstanceState.getString("currentNote");
            currentEmoji = savedInstanceState.getString("currentEmoji");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isDataLoaded", isDataLoaded);
        if (coupleId != null) outState.putString("coupleId", coupleId);
        if (partnerUid != null) outState.putString("partnerUid", partnerUid);
        if (edtNote != null && edtNote.getText() != null) {
            outState.putString("currentNote", edtNote.getText().toString());
        }
        if (emojiGroup != null) {
            int checkedId = emojiGroup.getCheckedRadioButtonId();
            if (checkedId != -1) {
                RadioButton selectedRadio = emojiGroup.findViewById(checkedId);
                if (selectedRadio != null) {
                    outState.putString("currentEmoji", selectedRadio.getText().toString());
                }
            }
        }
    }

    private void initializeData() {
        if (!isAdded() || getContext() == null) return;

        // Lấy coupleId → fetch member → load/subscribe
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    
                    coupleId = snap.getString("coupleId");
                    if (coupleId == null) {
                        toast("Bạn chưa thuộc cặp nào");
                        return;
                    }
                    fetchPartnerThenStart();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    toast("Lỗi đọc user: " + e.getMessage());
                });
    }

    // ===== Helpers =====
    private void toast(String s){ 
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show(); 
        }
    }

    private String todayStr(String fmt) {
        return new SimpleDateFormat(fmt, Locale.getDefault()).format(new Date());
    }

    private String todayDocId() { // yyyyMMdd
        return todayStr("yyyyMMdd");
    }

    private DocumentReference todayDocRef() {
        return db.collection("couples").document(coupleId)
                .collection("moods").document(todayDocId());
    }

    // ===== Save mood (merge theo entries.{uid}) =====
    private void saveMood() {
        if (!isAdded() || coupleId == null || emojiGroup == null) return;

        int checkedId = emojiGroup.getCheckedRadioButtonId();
        if (checkedId == -1) { toast("Chọn emoji"); return; }

        RadioButton selectedRadio = emojiGroup.findViewById(checkedId);
        if (selectedRadio == null) return;
        
        String emoji = selectedRadio.getText().toString();
        String note = (edtNote != null) ? edtNote.getText().toString() : "";
        
        // Save current state
        currentEmoji = emoji;
        currentNote = note;

        Map<String,Object> entry = new HashMap<>();
        entry.put("emoji", emoji);
        entry.put("note",  note);
        entry.put("ts",    FieldValue.serverTimestamp());

        Map<String,Object> data = new HashMap<>();
        Map<String,Object> entries = new HashMap<>();
        entries.put(me.getUid(), entry);
        data.put("entries", entries);

        todayDocRef().set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    
                    toast("Đã lưu mood");
                    updateMyMoodStatus(emoji, note);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    toast("Lỗi: " + e.getMessage());
                });
    }

    private void updateMyMoodStatus(String emoji, String note) {
        if (!isAdded() || txtMyMoodStatus == null) return;
        
        String status = "Mood của bạn: " + emoji;
        if (!TextUtils.isEmpty(note)) status += " - " + note.trim();
        txtMyMoodStatus.setText(status);
        txtMyMoodStatus.setVisibility(View.VISIBLE);
    }

    // ===== Điền lại mood của mình nếu đã có =====
    private void loadMyMood() {
        if (!isAdded()) return;
        
        todayDocRef().get().addOnSuccessListener(snap -> {
            if (!isAdded() || !snap.exists()) return;
            
            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null) return;
            Map<String,Object> mine = (Map<String,Object>) entries.get(me.getUid());
            if (mine == null) return;

            String emoji = (String) mine.get("emoji");
            String note  = (String) mine.get("note");

            if (!TextUtils.isEmpty(note) && edtNote != null) {
                edtNote.setText(note);
                currentNote = note;
            }
            
            if (!TextUtils.isEmpty(emoji) && emojiGroup != null) {
                restoreEmojiSelection(emoji);
                currentEmoji = emoji;
            }

            updateMyMoodStatus(emoji, note);
        });
    }

    private void restoreEmojiSelection(String emoji) {
        if (emojiGroup == null || TextUtils.isEmpty(emoji)) return;
        
        if ("😊".equals(emoji))      emojiGroup.check(R.id.emojiHappy);
        else if ("😐".equals(emoji)) emojiGroup.check(R.id.emojiNeutral);
        else if ("😢".equals(emoji)) emojiGroup.check(R.id.emojiSad);
        else if ("😠".equals(emoji)) emojiGroup.check(R.id.emojiAngry);
        else if ("🥰".equals(emoji)) emojiGroup.check(R.id.emojiLove);
    }

    // ===== Realtime mood người kia hôm nay =====
    private void watchPartnerMood() {
        if (!isAdded()) return;
        
        // Remove existing listener to prevent duplicates
        if (partnerListener != null) {
            partnerListener.remove();
            partnerListener = null;
        }

        partnerListener = todayDocRef().addSnapshotListener((snap, e) -> {
            if (!isAdded()) return; // Check if fragment is still attached
            
            if (e != null) { 
                if (txtPartnerMood != null) txtPartnerMood.setText("Lỗi quyền/kết nối"); 
                return; 
            }
            if (snap == null || !snap.exists()) { 
                if (txtPartnerMood != null) txtPartnerMood.setText("Chưa có mood hôm nay"); 
                return; 
            }

            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null || entries.isEmpty()) { 
                if (txtPartnerMood != null) txtPartnerMood.setText("Chưa có mood hôm nay"); 
                return; 
            }

            // Ưu tiên partnerUid; nếu null thì chọn uid khác mình trong entries
            String targetUid = partnerUid;
            if (TextUtils.isEmpty(targetUid) || !entries.containsKey(targetUid)) {
                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { targetUid = uid; break; }
            }
            if (TextUtils.isEmpty(targetUid) || !entries.containsKey(targetUid)) {
                if (txtPartnerMood != null) txtPartnerMood.setText("Chưa có mood hôm nay"); 
                return;
            }

            Map<String,Object> m = (Map<String,Object>) entries.get(targetUid);
            String emoji = m == null ? null : String.valueOf(m.get("emoji"));
            String note  = m == null ? null : String.valueOf(m.get("note"));

            String moodText = TextUtils.isEmpty(emoji) ? "😊" : emoji;
            if (!TextUtils.isEmpty(note) && !"null".equals(note)) {
                moodText += "\n" + note.trim();
            }
            if (txtPartnerMood != null) txtPartnerMood.setText(moodText);
        });
    }

    // ===== Lịch sử 7 ngày: "dd/MM: my | partner" =====
    private void loadHistory7days() {
        if (!isAdded()) return;
        
        final SimpleDateFormat idFmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        final SimpleDateFormat showFmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        List<String> ids = new ArrayList<>();
        for (int i=0; i<7; i++) { ids.add(idFmt.format(cal.getTime())); cal.add(Calendar.DATE, -1); }

        db.collection("couples").document(coupleId)
                .collection("moods")
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snaps -> {
                    if (!isAdded()) return;
                    
                    List<DocumentSnapshot> list = new ArrayList<>(snaps.getDocuments());
                    list.sort((a,b) -> b.getId().compareTo(a.getId())); // mới → cũ

                    StringBuilder sb = new StringBuilder();
                    for (DocumentSnapshot d : list) {
                        String id = d.getId(); // yyyyMMdd
                        Map<String,Object> entries = (Map<String,Object>) d.get("entries");

                        String myEmoji = "…";
                        String partnerEmoji = "…";

                        if (entries != null) {
                            Object meObj = entries.get(me.getUid());
                            if (meObj instanceof Map) {
                                Object e1 = ((Map<?,?>) meObj).get("emoji");
                                if (e1 != null) myEmoji = String.valueOf(e1);
                            }

                            String pUid = partnerUid;
                            if (TextUtils.isEmpty(pUid)) {
                                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { pUid = uid; break; }
                            }
                            if (!TextUtils.isEmpty(pUid)) {
                                Object pObj = entries.get(pUid);
                                if (pObj instanceof Map) {
                                    Object e2 = ((Map<?,?>) pObj).get("emoji");
                                    if (e2 != null) partnerEmoji = String.valueOf(e2);
                                }
                            }
                        }

                        try {
                            Date date = idFmt.parse(id);
                            sb.append(showFmt.format(date));
                        } catch (Exception ignore) {
                            sb.append(id);
                        }
                        sb.append(": ").append(myEmoji).append(" | ").append(partnerEmoji).append("\n");
                    }
                    
                    if (txtHistory != null) {
                        txtHistory.setText(sb.toString());
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || txtHistory == null) return;
                    txtHistory.setText("Không tải được lịch sử: " + e.getMessage());
                });
    }

    // ===== Đọc partnerUid + tên rồi khởi động các luồng =====
    private void fetchPartnerThenStart() {
        if (!isAdded()) return;
        
        db.collection("couples").document(coupleId).get()
                .addOnSuccessListener(cpl -> {
                    if (!isAdded()) return;
                    
                    List<String> members = (List<String>) cpl.get("members");
                    if (members != null) {
                        for (String uid : members) if (!uid.equals(me.getUid())) partnerUid = uid;
                    }

                    if (!TextUtils.isEmpty(partnerUid)) {
                        db.collection("users").document(partnerUid).get()
                                .addOnSuccessListener(u -> {
                                    if (!isAdded()) return;
                                    
                                    // ưu tiên displayName; fallback email; cuối cùng "Người yêu"
                                    String name = nvl(u.getString("displayName"),
                                            nvl(u.getString("name"),
                                                    nvl(u.getString("email"), "Người yêu")));
                                    if (txtPartnerName != null) {
                                        txtPartnerName.setText("Mood của " + name);
                                    }
                                });
                    }

                    loadMyMood();
                    watchPartnerMood();
                    loadHistory7days();
                    isDataLoaded = true; // Mark data as loaded
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    toast("Lỗi đọc couple: " + e.getMessage());
                });
    }

    private static String nvl(String s, String alt) { return TextUtils.isEmpty(s) ? alt : s; }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (partnerListener != null) { partnerListener.remove(); partnerListener = null; }
    }
}
