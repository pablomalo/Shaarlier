package com.dimtion.shaarlier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;


public class AddActivity extends Activity {
    private Map<String, String> coockies;
    private String token;
    private String urlShaarli;
    private String username;
    private String password;
    private Boolean privateShare;
    private boolean prefOpenDialog;

    private View a_dialogView;
    private AsyncTask a_TitleGetterExec;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_add);
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        // Check if the user had his data validated :
        SharedPreferences pref = getSharedPreferences(getString(R.string.params), MODE_PRIVATE);
        urlShaarli  = pref.getString(getString(R.string.p_url_shaarli), "");
        username    = pref.getString(getString(R.string.p_username), "");
        password    = pref.getString(getString(R.string.p_password),"");
        boolean vld = pref.getBoolean(getString(R.string.p_validated), false);
        privateShare = pref.getBoolean(getString(R.string.p_default_private), true);
        prefOpenDialog = pref.getBoolean(getString(R.string.p_show_share_dialog), false);
        
        // convert urlShaarli into a real url :
        if(!urlShaarli.endsWith("/")){
            urlShaarli +='/';
        }
        if (!(urlShaarli.startsWith("http://") || urlShaarli.startsWith("https://"))){
            urlShaarli = "http://" + urlShaarli;
        }
        
        if(username.equals("") || password.equals("") || !vld){
            // If the is an error, launch the settings :
            Intent intentLaunchSettings = new Intent(this, MainActivity.class);
            startActivity(intentLaunchSettings);
        } else if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
                // Show edit dialog if the users wants :
                if(prefOpenDialog){
                    handleDialog(sharedUrl);
                } else {
                    new HandleAddUrl().execute(sharedUrl, "", "", "");
                }

            } else {
                Toast.makeText(getApplicationContext(), R.string.add_not_handle, Toast.LENGTH_SHORT).show();
            }
        }
        
    }
    //
    // Method made to handle the dialog box
    //
    private void handleDialog(final String sharedUrl){
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppTheme));

        LayoutInflater inflater = AddActivity.this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.share_dialog, null);
        ((CheckBox) dialogView.findViewById(R.id.private_share)).setChecked(privateShare);
        this.a_dialogView = dialogView;
        
        loadAutoTitle(sharedUrl);
        
        
        
        builder.setView(dialogView)
                .setTitle(R.string.share)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Retrieve interface : 
                        String title = ((EditText) dialogView.findViewById(R.id.title)).getText().toString();
                        String description = ((EditText) dialogView.findViewById(R.id.description)).getText().toString();
                        String tags = ((EditText) dialogView.findViewById(R.id.tags)).getText().toString();
                        privateShare = ((CheckBox) dialogView.findViewById(R.id.private_share)).isChecked();
                        
                        new HandleAddUrl().execute(sharedUrl, title, description, tags);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    // To get an automatic title :
    private void loadAutoTitle(String sharedUrl){
        
        a_dialogView.findViewById(R.id.loading_title).setVisibility(View.VISIBLE);
        ((EditText) a_dialogView.findViewById(R.id.title)).setHint(R.string.loading_title_hint);

        final GetPageTitle getter = new GetPageTitle();
        a_TitleGetterExec = getter.execute(sharedUrl);
        ((EditText) a_dialogView.findViewById(R.id.title)).addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                getter.cancel(true);
                a_dialogView.findViewById(R.id.loading_title).setVisibility(View.GONE);
            }
            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
        
    }
    private void updateTitle(String title, boolean isError){
        ((EditText) a_dialogView.findViewById(R.id.title)).setHint(R.string.title_hint);
        
        if(isError){
            ((EditText) a_dialogView.findViewById(R.id.title)).setHint(R.string.error_retrieving_title);
        } else if(!title.equals("")) {
            ((EditText) a_dialogView.findViewById(R.id.title)).setText(title);
        }

        a_dialogView.findViewById(R.id.loading_title).setVisibility(View.GONE);
        
    }
    
    //
    // Class which handle the arrival of a new share url, async
    //
    private class HandleAddUrl extends AsyncTask<String, Void, Boolean> {
        
        @Override
        protected Boolean doInBackground(String... url){
            // Wait for the title to be retrieved :
            String loadedTitle;
            try {
                loadedTitle = (String) a_TitleGetterExec.get();
            } catch (InterruptedException e) {
                loadedTitle = "";
            } catch (ExecutionException e){
                loadedTitle = "";
            } catch (CancellationException e){
                loadedTitle = "";
            }
            try {
                // Connect the user to the site :
                connect();
                
                String sharedTitle;
                if(url[1].equals("")){
                    sharedTitle = loadedTitle;
                } else {
                    sharedTitle = url[1];
                }
                // Post the shared url :
                postLink(url[0], sharedTitle, url[2], url[3]);
                
            } catch (IOException | NullPointerException e){
                return false;
            }
            return true;
        }
        
        @Override
        protected void onPostExecute(Boolean posted){
            if (posted){
                Toast.makeText(getApplicationContext(), R.string.add_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), R.string.add_error, Toast.LENGTH_LONG).show();
            }
            finish();
        }
        
        private String getToken() throws IOException {            
            final String loginFormUrl = urlShaarli.concat("?do=login");

            Connection.Response loginFormPage = Jsoup.connect(loginFormUrl)
                    .method(Connection.Method.GET)
                    .execute();
            Document loginPageDoc = loginFormPage.parse();

            
            Element tokenElement = loginPageDoc.body().select("input[name=token]").first();
            coockies = loginFormPage.cookies();
            return tokenElement.attr("value");
        }
        
        
        private void connect() throws IOException {
            final String loginUrl = urlShaarli;
            token = getToken();
            
            // The actual request
            Connection.Response loginPage = Jsoup.connect(loginUrl)
                    .followRedirects(true)
                    .method(Connection.Method.POST)
                    .cookies(coockies)
                    .data("login", username)
                    .data("password", password)
                    .data("token", token)
                    .data("returnurl", urlShaarli)
                    .execute();
            coockies = loginPage.cookies();
            Document document = loginPage.parse();
            Element logoutElement = document.body().select("a[href=?do=logout]").first();
            logoutElement.attr("href"); // If this fails, you're not connected
        }
        
        
        private void postLink(String url, String title, String description, String tags) 
                throws IOException {
            String encodedShareUrl = URLEncoder.encode(url, "UTF-8");
            
            // Get a token and a date :
            final String postFormUrl = urlShaarli + "?post=" + encodedShareUrl;
            Connection.Response formPage = Jsoup.connect(postFormUrl)
                    .followRedirects(true)
                    .cookies(coockies)
                    .execute();
            // coockies = formPage.cookies();
            Document formPageDoc = formPage.parse();
            // String debug = formPage.body();
            Element tokenElement = formPageDoc.body().select("input[name=token]").first();
            token = tokenElement.attr("value");
            
            Element dateElement = formPageDoc.body().select("input[name=lf_linkdate]").first();
            String date = dateElement.attr("value"); // The server date
            
            if(title.equals("")) {
                Element titleElement = formPageDoc.body().select("input[name=lf_title]").first();
                title = titleElement.attr("value"); // The title given by shaarli
            }
            // The post request :
            final String postUrl = urlShaarli + "?post=" + encodedShareUrl;
            Connection postPageConn = Jsoup.connect(postUrl)
                    .method(Connection.Method.POST)
                    .cookies(coockies)
                    .data("save_edit", "Save")
                    .data("token", token)
                    .data("lf_tags", tags)
                    .data("lf_linkdate", date)
                    .data("lf_url", url)
                    .data("lf_title", title)
                    .data("lf_description", description);
            if (privateShare) postPageConn.data("lf_private","on");
            postPageConn.execute(); // Then we post

            // String debug = postPage.body();
            
        }
    }

    private class GetPageTitle extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... url){
            try {
                Connection.Response pageResp = Jsoup.connect(url[0])
                        .maxBodySize(10240) // Hopefully we won't need more data
                        .followRedirects(true)
                        .execute();
                Document pageDoc = pageResp.parse();
                return pageDoc.title();
            } catch (IOException | NullPointerException e) {
                return "";
            }

        }

        @Override
        protected void onPostExecute(String title){
            if (title.equals("")) {
                updateTitle(title, true);
            } else {
                updateTitle(title, false);
            }
        }
        
        @Override
        protected void onCancelled(String title){
            updateTitle("", false);
        }
    }
    }

    