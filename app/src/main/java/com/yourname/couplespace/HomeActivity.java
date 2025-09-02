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
    // Fragment caching to avoid recreation
    private MoodFragment moodFragment;
    private DailyQFragment dailyQFragment;
    private QuizFragment quizFragment;
    private DrawFragment drawFragment;
    private Fragment currentFragment;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("Home");

        // Initialize fragments only once
        if (savedInstanceState == null) {
            initFragments();
        } else {
            // Restore fragments from fragment manager after configuration change
            restoreFragments();
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            Fragment targetFragment = getFragmentForNavItem(item.getItemId());
            
            if (targetFragment != null && targetFragment != currentFragment) {
                switchToFragment(targetFragment);
            }
            return true;
        });

        // Tab mặc định
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_mood);
        }
    }

    private void initFragments() {
        moodFragment = new MoodFragment();
        dailyQFragment = new DailyQFragment();
        quizFragment = new QuizFragment();
        drawFragment = new DrawFragment();
    }

    private void restoreFragments() {
        // Try to restore fragments from fragment manager
        moodFragment = (MoodFragment) getSupportFragmentManager().findFragmentByTag("MOOD");
        dailyQFragment = (DailyQFragment) getSupportFragmentManager().findFragmentByTag("DAILYQ");
        quizFragment = (QuizFragment) getSupportFragmentManager().findFragmentByTag("QUIZ");
        drawFragment = (DrawFragment) getSupportFragmentManager().findFragmentByTag("DRAW");
        
        // Create new instances if fragments weren't found
        if (moodFragment == null) moodFragment = new MoodFragment();
        if (dailyQFragment == null) dailyQFragment = new DailyQFragment();
        if (quizFragment == null) quizFragment = new QuizFragment();
        if (drawFragment == null) drawFragment = new DrawFragment();
    }

    private Fragment getFragmentForNavItem(int itemId) {
        if (itemId == R.id.nav_mood) return moodFragment;
        else if (itemId == R.id.nav_dailyq) return dailyQFragment;
        else if (itemId == R.id.nav_quiz) return quizFragment;
        else return drawFragment;
    }

    private void switchToFragment(Fragment fragment) {
        if (fragment == null) return;

        String tag = getTagForFragment(fragment);
        
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment, tag)
                .commitAllowingStateLoss(); // Use commitAllowingStateLoss to prevent crashes
        
        currentFragment = fragment;
    }

    private String getTagForFragment(Fragment fragment) {
        if (fragment instanceof MoodFragment) return "MOOD";
        else if (fragment instanceof DailyQFragment) return "DAILYQ";
        else if (fragment instanceof QuizFragment) return "QUIZ";
        else return "DRAW";
    }
}
