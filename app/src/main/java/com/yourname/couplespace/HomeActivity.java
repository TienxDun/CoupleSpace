package com.yourname.couplespace;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.yourname.couplespace.ui.DailyQFragment;
import com.yourname.couplespace.ui.DrawFragment;
import com.yourname.couplespace.ui.MoodFragment;
import com.yourname.couplespace.ui.QuizFragment;

public class HomeActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);
        setTitle("Home");

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if (id == R.id.nav_mood)      f = new MoodFragment();
            else if (id == R.id.nav_dailyq) f = new DailyQFragment();
            else if (id == R.id.nav_quiz)   f = new QuizFragment();
            else                             f = new DrawFragment();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, f)
                    .commit();
            return true;
        });

        // Tab mặc định
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_mood);
        }
    }
}
