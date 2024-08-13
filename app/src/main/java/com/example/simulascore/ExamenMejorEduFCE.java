package com.example.simulascore;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExamenMejorEduFCE extends AppCompatActivity {

    private TextView tvQuestion, profileName;
    private ImageView ivQuestionImage;
    private RadioGroup rgOptions;
    private RadioButton rbOption1, rbOption2, rbOption3, rbOption4;
    private Button btnNext, btnPrevious, btn_send;
    private List<Question> questionListFCE;
    private List<Integer> userAnswersFCE;
    private int currentQuestionIndex = 0;

    private static final String TAG = "ExamenFCEActivity";
    private String email;

    private Handler handler = new Handler();
    private Runnable saveAnswersRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_examen_mejoredu_fce);

        tvQuestion = findViewById(R.id.tv_question);
        ivQuestionImage = findViewById(R.id.iv_question_image);
        rgOptions = findViewById(R.id.rg_options);
        rbOption1 = findViewById(R.id.rb_option1);
        rbOption2 = findViewById(R.id.rb_option2);
        rbOption3 = findViewById(R.id.rb_option3);
        rbOption4 = findViewById(R.id.rb_option4);
        btnNext = findViewById(R.id.btn_next);
        btnPrevious = findViewById(R.id.btn_previous);
        btn_send = findViewById(R.id.btn_send_exam);
        profileName = findViewById(R.id.tv_student_info);

        btn_send.setVisibility(View.INVISIBLE);


        questionListFCE = new ArrayList<>();
        userAnswersFCE = new ArrayList<>();

        // Recuperar el email de SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        email = sharedPreferences.getString("email", null);

        if (email == null) {
            Toast.makeText(this, "No se encontró el email. Por favor, inicie sesión de nuevo.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserAnswer();
                if (currentQuestionIndex < questionListFCE.size() - 1) {
                    currentQuestionIndex++;
                    displayQuestion();
                } else {
                    Toast.makeText(ExamenMejorEduFCE.this, "Has completado todas las preguntas", Toast.LENGTH_SHORT).show();
                    btn_send.setVisibility(View.VISIBLE);
                }
            }
        });

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn_send.setVisibility(View.INVISIBLE);
                saveUserAnswer();
                if (currentQuestionIndex > 0) {
                    currentQuestionIndex--;
                    displayQuestion();
                } else {
                    Toast.makeText(ExamenMejorEduFCE.this, "Estás en la primera pregunta", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (areAllQuestionsAnswered()) {
                    Toast.makeText(ExamenMejorEduFCE.this, "Examen enviado", Toast.LENGTH_SHORT).show();
                    sendAllAnswers();
                    //  sendUserAnswersToServer();
                } else {
                    Toast.makeText(ExamenMejorEduFCE.this, "Por favor, responda todas las preguntas antes de enviar el examen.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        fetchQuestions();
        fetchAlumnoInfo(email);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(saveAnswersRunnable); // Detener el Runnable cuando la actividad se destruya
    }
    private boolean areAllQuestionsAnswered() {
        for (int answer : userAnswersFCE) {
            if (answer == -1) {
                return false; // Hay una pregunta sin responder en Español
            }
        }

        return true;
    }

    private void fetchQuestions() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Cargando preguntas...");
        progressDialog.show();

        String url = "https://simulascore.com/apis/moduloAlumno/examenFCE/obtenerPreguntasFCE.php?email=" + email;
        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        progressDialog.dismiss();
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            String status = jsonResponse.getString("status");

                            if (status.equals("success")) {
                                JSONArray data = jsonResponse.getJSONArray("data");

                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject questionObject = data.getJSONObject(i);
                                    String questionText = questionObject.getString("pregunta");
                                    String option1 = questionObject.getString("respuesta1");
                                    String option2 = questionObject.getString("respuesta2");
                                    String option3 = questionObject.getString("respuesta3");
                                    String option4 = questionObject.getString("respuesta4");
                                    String imageUrl = questionObject.getString("url_imagen");
                                    String additionalText = questionObject.optString("texto_adicional", "");

                                    questionListFCE.add(new Question(
                                            questionObject.getString("id_pregunta"),
                                            questionText, option1, option2, option3, option4, imageUrl, additionalText));
                                    userAnswersFCE.add(-1); // Inicializar las respuestas del usuario con -1 (sin respuesta)
                                }

                                displayQuestion();
                                fetchSavedAnswers(); // Llamar a fetchSavedAnswers() después de que las preguntas se hayan cargado
                            } else {
                                String message = jsonResponse.getString("message");
                                Toast.makeText(ExamenMejorEduFCE.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(ExamenMejorEduFCE.this, "Error en la respuesta del servidor", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                progressDialog.dismiss();
                Toast.makeText(ExamenMejorEduFCE.this, "Error al cargar preguntas", Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
    }

    private void fetchSavedAnswers() {
        String url = "https://simulascore.com/apis/moduloAlumno/examenFCE/getSavedAnswerFCE.php?email=" + email;
        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            String status = jsonResponse.getString("status");

                            if (status.equals("success")) {
                                JSONArray savedAnswersArray = jsonResponse.getJSONArray("data");

                                if (savedAnswersArray.length() == 0) {
                                    startAutoSave();
                                    return; // No hacer nada si el array está vacío
                                }

                                for (int i = 0; i < savedAnswersArray.length(); i++) {
                                    JSONObject answerObject = savedAnswersArray.getJSONObject(i);
                                    String questionId = answerObject.getString("questionId");
                                    String answerValue = answerObject.getString("answerValue");

                                    int answerIndex = Integer.parseInt(answerValue.replace("respuesta", "")) - 1;

                                    // Buscar la pregunta y actualizar la respuesta
                                    for (int j = 0; j < questionListFCE.size(); j++) {
                                        if (questionListFCE.get(j).getId().equals(questionId)) {
                                            userAnswersFCE.set(j, answerIndex);
                                            break;
                                        }
                                    }
                                }

                                displayQuestion(); // Actualizar la visualización de preguntas con respuestas guardadas

                                // Iniciar el guardado automático solo después de cargar las respuestas guardadas
                                startAutoSave();
                            } else {
                                String message = jsonResponse.getString("message");
                                Toast.makeText(ExamenMejorEduFCE.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(ExamenMejorEduFCE.this, "Error en la respuesta del servidor", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(ExamenMejorEduFCE.this, "Error al cargar respuestas guardadas: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
    }

    private void startAutoSave() {
        saveAnswersRunnable = new Runnable() {
            @Override
            public void run() {
                sendUserAnswersToServer();
                handler.postDelayed(this, 10000); // Ejecutar cada 60 segundos (1 minuto)
            }
        };
        handler.post(saveAnswersRunnable); // Iniciar el Runnable
    }

    private void displayQuestion() {
        Question currentQuestion = questionListFCE.get(currentQuestionIndex);
        tvQuestion.setText(currentQuestion.getQuestionText());
        rbOption1.setText(currentQuestion.getOption1());
        rbOption2.setText(currentQuestion.getOption2());
        rbOption3.setText(currentQuestion.getOption3());
        rbOption4.setText(currentQuestion.getOption4());
        rgOptions.clearCheck();

        String imageUrl = currentQuestion.getImageUrl();
        if (!imageUrl.isEmpty()) {
            ivQuestionImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load("https://simulascore.com" + imageUrl.replace("\\", "/"))
                    .into(ivQuestionImage);
        } else {
            ivQuestionImage.setVisibility(View.GONE);
        }
        if (!currentQuestion.getAdditionalText().isEmpty()) {
            // Suponiendo que tienes un TextView adicional en tu layout para mostrar el texto adicional
            TextView tvAdditionalText = findViewById(R.id.tv_textoAdicional);
            tvAdditionalText.setVisibility(View.VISIBLE);
            tvAdditionalText.setText(currentQuestion.getAdditionalText());
        } else {
            TextView tvAdditionalText = findViewById(R.id.tv_textoAdicional);
            tvAdditionalText.setVisibility(View.GONE);
        }

        // Restaurar la respuesta del usuario
        int savedAnswer = userAnswersFCE.get(currentQuestionIndex);
        if (savedAnswer != -1) {
            ((RadioButton) rgOptions.getChildAt(savedAnswer)).setChecked(true);
        }
    }

    private void saveUserAnswer() {
        int selectedId = rgOptions.getCheckedRadioButtonId();
        int answerIndex = -1;
        if (selectedId == rbOption1.getId()) {
            answerIndex = 0;
        } else if (selectedId == rbOption2.getId()) {
            answerIndex = 1;
        } else if (selectedId == rbOption3.getId()) {
            answerIndex = 2;
        } else if (selectedId == rbOption4.getId()) {
            answerIndex = 3;
        }
        userAnswersFCE.set(currentQuestionIndex, answerIndex);
    }

    private void sendUserAnswersToServer() {
        String url = "https://simulascore.com/apis/moduloAlumno/examenFCE/guardarRespuestasFCE.php";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);

            JSONArray answersArray = new JSONArray();
            for (int i = 0; i < questionListFCE.size(); i++) {
                JSONObject answerObject = new JSONObject();
                answerObject.put("questionId", questionListFCE.get(i).getId());
                answerObject.put("answerValue", "respuesta" + (userAnswersFCE.get(i) + 1));
                answerObject.put("answerText", getAnswerText(questionListFCE.get(i), userAnswersFCE.get(i)));
                answersArray.put(answerObject);
            }

            requestBody.put("answers", answersArray);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Respuestas guardadas exitosamente: " + response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error al guardar respuestas: " + error.getMessage());
            }
        }) {
            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
    }

    private void sendAllAnswers() {
        String url = "https://simulascore.com/apis/moduloAlumno/examenFCE/finalizarExamenFCE.php";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);

            JSONArray answersArray = new JSONArray();
            for (int i = 0; i < questionListFCE.size(); i++) {
                JSONObject answerObject = new JSONObject();
                answerObject.put("questionId", questionListFCE.get(i).getId());
                answerObject.put("answerValue", "respuesta" + (userAnswersFCE.get(i) + 1));
                answerObject.put("answerText", getAnswerText(questionListFCE.get(i), userAnswersFCE.get(i)));
                answersArray.put(answerObject);
            }

            requestBody.put("answers", answersArray);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Respuestas guardadas exitosamente: " + response);
                        Intent intent = new Intent(ExamenMejorEduFCE.this, ExamenMejorEduSeleccionarMateria.class);
                        startActivity(intent);
                        finish();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error al guardar respuestas: " + error.getMessage());
            }
        }) {
            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
    }

    private String getAnswerText(Question question, int answerIndex) {
        switch (answerIndex) {
            case 0:
                return question.getOption1();
            case 1:
                return question.getOption2();
            case 2:
                return question.getOption3();
            case 3:
                return question.getOption4();
            default:
                return "";
        }
    }
    private void fetchAlumnoInfo(String email) {
        String url = "https://simulascore.com/apis/moduloAlumno/getAlumnoInfo.php?email=" + email;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        String status = jsonResponse.getString("status");

                        if (status.equals("success")) {
                            JSONObject data = jsonResponse.getJSONObject("data");
                            String nombre = data.getString("nombre");
                            String apellido = data.getString("apellido");

                            profileName.setText("Nombre: "+nombre + " " + apellido);



                        } else {
                            String message = jsonResponse.getString("message");
                            Log.e(TAG, "Error: " + message);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "JSON Exception: " + e.getMessage());
                    }
                }, error -> {
            if (error.networkResponse != null) {
                String errorMsg = new String(error.networkResponse.data);
                Log.e(TAG, errorMsg);
            } else {
                Log.e(TAG, error.getMessage());
            }
        });

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
    }
}