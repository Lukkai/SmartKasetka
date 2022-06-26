package com.example.smartkasetka;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;

public class SettingsActivity extends MainActivity{
    Button programmingB, saveContactB;
    EditText numberE, messageE;
    String messageS;
    String numberS;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        numberE = (EditText)findViewById(R.id.numberE);
        saveContactB = (Button) findViewById(R.id.saveContactB);
        programmingB = (Button)findViewById(R.id.programmingB);
        messageE = (EditText)findViewById(R.id.messageE);

        saveContactB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                messageS = String.valueOf(messageE.getText());
                numberS = String.valueOf(numberE.getText());
                Intent myIntent = new Intent(SettingsActivity.this, MainActivity.class);
                myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(myIntent);
                //finish();
            }
        });

    }
}
