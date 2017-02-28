package org.cs5431_client.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Client extends Application {

    static Parent loginNode;
    static Parent fileViewNode;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            final URL r = getClass().getResource("login.fxml");

            if (r == null) {
                System.err.println("Could not find the layout!");
                tryStopping();
            } else {
                loginNode = FXMLLoader.load(r);
                final Scene scene = new Scene(loginNode);
                stage.setScene(scene);
                stage.setTitle("CS 5431: Pretty Secure File Sharing");
                stage.show();

            }
        } catch (IOException e) {
            printNonFatalError("Failed to load layout");
            e.printStackTrace();
            tryStopping();
        }
    }

    private void tryStopping() {
        try {
            stop();
        } catch (Exception e) {
            printNonFatalError("Couldn't stop...");
        }
    }

    static void printNonFatalError(String message) {
        //TODO: reconsider if printing to standard error is correct behaviour
        System.err.println(message);
    }
}
