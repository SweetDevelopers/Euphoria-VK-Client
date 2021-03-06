package ru.euphoriadev.vk;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatEditText;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

import ru.euphoriadev.vk.api.Api;
import ru.euphoriadev.vk.api.model.VKFullUser;
import ru.euphoriadev.vk.api.model.VKResolveScreenName;
import ru.euphoriadev.vk.api.model.VKUser;
import ru.euphoriadev.vk.util.Account;
import ru.euphoriadev.vk.util.AndroidUtils;
import ru.euphoriadev.vk.util.ThemeManager;
import ru.euphoriadev.vk.util.ThreadExecutor;


/**
 * Created by Igor on 08.02.15.
 */
public class WelcomeActivity extends AppCompatActivity implements View.OnClickListener {

    private final int REQUEST_LOGIN = 1;
    Account account;
    Api api;
    private Button btnAccessToken, btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        setTitle(R.string.authorization);


//        // Стилизируем текст
//        Spannable text = new SpannableString("Привет, человек!");
//        text.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 7, 15,  Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        text.setSpan(new ForegroundColorSpan(Color.GREEN), 7, 15,  Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        Toast.makeText(this, text, Toast.LENGTH_LONG).show();

        account = new Account(getApplicationContext());
//        // Восстанавливаем сохраненную сесию
//        account.restore();

        //Если сессия есть, переходим на главную активити
//        if (account.access_token != null) {
//            startActivity(new Intent(this, BasicActivity.class));
//            finish();
//            return;
//        }

        btnLogin = (Button) findViewById(R.id.btnLogin);
        btnAccessToken = (Button) findViewById(R.id.btnAccessToken);
        ImageView ivLogin = (ImageView) findViewById(R.id.ivLogin);

        ivLogin.setOnClickListener(this);
        btnLogin.setOnClickListener(this);
        btnAccessToken.setOnClickListener(this);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) {
            //авторизовались успешно
            account.access_token = data.getStringExtra("token");
            account.user_id = data.getLongExtra("user_id", 0);
            //      account.save();
            // Создаем  API
            api = Api.init(account);
            Toast.makeText(this, getResources().getString(R.string.authorization_successfully), Toast.LENGTH_LONG).show();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        api.setOffline();

                        ArrayList<Long> userIds = new ArrayList<Long>();
                        userIds.add(account.user_id);

                        ArrayList<VKFullUser> profiles = api.getProfilesFull(userIds, null, "status, photo_100", null, null, null);
                        for (VKFullUser user : profiles) {
                            account.fullName = user.first_name + " " + user.last_name;
                            account.photo = user.photo_medium_rec;
                            account.status = user.status;
                        }
                        account.save();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(new Intent(getApplicationContext(), BasicActivity.class));
                                finish();
//                                AndroidUtils.showToast(getApplicationContext(), "Оффлайн включен!", true);
                            }
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnAccessToken:
                makeLoginDialog();
                break;

            case R.id.btnLogin:
                Intent intent = new Intent();
                intent.setClass(this, LoginActivity.class);
                startActivityForResult(intent, REQUEST_LOGIN);
                break;
        }


    }

    private void makeLoginDialog() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final EditText etUserId = new AppCompatEditText(this);
        final EditText etAccessToken = new AppCompatEditText(this);

        etUserId.setHint("User ID");
        etAccessToken.setHint("Access token");

//              etUserId.setInputType(InputType.TYPE_CLASS_NUMBER);

        etUserId.setLayoutParams(params);
        etAccessToken.setLayoutParams(params);

        final TextInputLayout inputLayoutUserId = new TextInputLayout(this);
        final TextInputLayout inputLayoutAccessToken = new TextInputLayout(this);

        inputLayoutUserId.addView(etUserId);
        inputLayoutAccessToken.addView(etAccessToken);

        LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(params);
        layout.setPadding(
                AndroidUtils.pxFromDp(this, 16),
                AndroidUtils.pxFromDp(this, 6),
                AndroidUtils.pxFromDp(this, 16),
                AndroidUtils.pxFromDp(this, 6));
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(inputLayoutUserId);
        layout.addView(inputLayoutAccessToken);

        AlertDialog.Builder builder = new AlertDialog.Builder(WelcomeActivity.this);
        builder.setTitle(getString(R.string.login));
        builder.setMessage(getString(R.string.login_description));
        builder.setView(layout);
        builder.setPositiveButton("Go!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (TextUtils.isEmpty(etAccessToken.getText().toString().trim()) ||
                        TextUtils.isEmpty(etAccessToken.getText().toString().trim())) {
                    Toast.makeText(WelcomeActivity.this, "Все поля необходимо заполнить", Toast.LENGTH_LONG).show();
                    return;
                }

                final String id = etUserId.getText().toString().trim();
                final String token = etAccessToken.getText().toString().trim();

                startLogin(id, token);
            }
        });
        builder.create().show();
    }

    private void startLogin(final String idOrScreenName, final String accessToken) {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Api api = Api.init(accessToken, Account.API_ID);

                if (idOrScreenName.startsWith("@")) {
                    VKResolveScreenName object_id = null;
                    try {
                        object_id = api.utilsResolveScreenName(idOrScreenName.replace("@", ""));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (object_id == null || object_id.object_id == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(WelcomeActivity.this, "Ошибка. Неверный id", Toast.LENGTH_LONG).show();
                            }
                        });
                        return;

                    }
                    account = new Account(WelcomeActivity.this);
                    account.access_token = accessToken;
                    account.user_id = object_id.object_id;

                    final VKUser vkUser = api.getProfile(account.user_id);
                    account.fullName = vkUser.toString();
                    account.photo = vkUser.photo_50;
                    account.save();

                    startActivity(new Intent(WelcomeActivity.this, BasicActivity.class)

                    );

                } else {

                    account = new Account(WelcomeActivity.this);
                    account.access_token = accessToken;
                    account.user_id = Long.parseLong(idOrScreenName);

                    final VKUser vkUser = api.getProfile(account.user_id);
                    account.fullName = vkUser.toString();
                    account.photo = vkUser.photo_50;
                    account.save();
                }
                finish();
            }

        });
    }
}
