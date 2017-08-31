package com.portallium.notekeeper.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.portallium.notekeeper.R;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.list.ListActivity;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import io.fabric.sdk.android.Fabric;

/**
 * Класс, внутри которого происходит взаимодействие со всей системой аутентификации, предоставляемой firebase.
 */
public class FirebaseLoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 1;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                if (currentUser != null) {
                    // User is signed in
                    logUserIn(currentUser);
                } else {
                    // User is signed out
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setProviders(Collections.singletonList(
                                            new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build()))
                                    .build(),
                            RC_SIGN_IN);
                    //TODO: авторизация без интернета, увы, невозможна. Есть ли у меня способ как-то прервать попытку авторизации после истечения определенного времени?
                }
            }
        };
    }

    /**
     * Метод обратного вызова, выполняемый при завершении процесса авторизации. Завершает текущую Activity.
     * @param requestCode  всегда равен 1. В противном случае ничего не произойдет.
     * @param resultCode если равен RESULT_OK, для авторизованного пользователя запускается ListActivity со списком блокнотов.
     * @param data не используется.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Sign-in succeeded, set up the UI
                logUserIn(mAuth.getCurrentUser());
                //важно понимать, что listener здесь не срабатывает. Не знаю, моя ли это вина, или так и задумано.
                //fixme: так. а в каком состоянии находится эта активность, когда этот метод вызывается? точно не в resumed же. либо paused, либо stopped. тогда логично, что лиснер не срабатывает.
            } else if (resultCode == RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    /**
     * Создает объект класса Intent, который можно использовать для вызова FirebaseLoginActivity.
     * Разлогинивает авторизованного пользователя.
     * @param activity Activity, из которой вызывается метод.
     */
    public static Intent getIntent(Activity activity) {
        AuthUI.getInstance().signOut(activity);
        return new Intent(activity, FirebaseLoginActivity.class);
    }

    private int returnUserIdByEmail(String email) {
        StorageKeeper.GetUserIdByEmailTask task = StorageKeeper.getInstance(this, mAuth.getCurrentUser().getUid()).new GetUserIdByEmailTask();
        task.execute(email);
        try {
            return task.get();
        }
        catch (InterruptedException | ExecutionException ex) {
            Log.e("Getting user ID", ex.getMessage(), ex);
            return -1;
        }
    }

    private void logUserIn(FirebaseUser user) {
        //todo: добавить верификацию по email
        InitializeCrashlytics(user);
        int currentUserId = returnUserIdByEmail(user.getEmail());
        Log.d("User is signed in", "his/her id = " + currentUserId);
        //todo: вот тут, по идее, должен запускаться метод синхронизации!
        startActivity(ListActivity.getIntent(FirebaseLoginActivity.this, currentUserId, user.getUid()));
        finish();
    }

    private void InitializeCrashlytics(FirebaseUser user) {
        Crashlytics.setUserIdentifier(user.getUid());
        Crashlytics.setUserEmail(user.getEmail());
        Crashlytics.setUserName(user.getDisplayName());
    }

}
//TODO: теоретически, эту активность можно превратить во фрагмент. подумать над этим.
