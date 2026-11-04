package ph.edu.dlsu.anilink.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.service.SupabaseService;

/**
 * JavaFX controller for the user registration view.
 * <p>
 * This class manages the user interface for creating new accounts. It captures
 * user input, performs data validation (such as enforcing the use of DLSU email addresses),
 * and maps the selected user category (Student, Admin, Driver) to the corresponding
 * system role. The registration process delegates data persistence to the
 * {@link SupabaseService}. Furthermore, it utilizes the Spring {@link ApplicationContext}
 * to act as a controller factory when transitioning back to the Login scene.
 * </p>
 */
@Controller
public class RegisterController {

    private final SupabaseService supabaseService;
    private final ApplicationContext applicationContext;

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private PasswordField passwordField;
    @FXML private Button registerButton;
    @FXML private Button backToLoginButton;
    @FXML private Label messageLabel;

    @Autowired
    public RegisterController(SupabaseService supabaseService, ApplicationContext applicationContext) {
        this.supabaseService = supabaseService;
        this.applicationContext = applicationContext;
    }

    @FXML
    private void initialize() {
        categoryComboBox.setItems(FXCollections.observableArrayList(
                "STUDENT / FACULTY", "ADMIN", "DRIVER"
        ));
    }

    @FXML
    private void handleRegister() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String selection = categoryComboBox.getValue();
        String password = passwordField.getText();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || selection == null) {
            showMessage("Please fill in all required fields.", true);
            return;
        }

        if (!email.toLowerCase().endsWith("@dlsu.edu.ph")) {
            showMessage("Please use a valid DLSU email address.", true);
            return;
        }

        // Determine the actual database Role based on the dropdown selection
        String dbRole;
        if ("ADMIN".equals(selection)) {
            dbRole = "ADMINISTRATOR";
        } else if ("DRIVER".equals(selection)) {
            dbRole = "DRIVER";
        } else {
            dbRole = "PASSENGER"; // STUDENT/FACULTY defaults to PASSENGER
        }

        try {
            // Pass both the dbRole and the original selection to the service
            supabaseService.registerAccount(name, email, password, dbRole, selection);
            showMessage("Account created successfully!", false);
            handleGoToLogin();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            e.printStackTrace();
            System.err.println("Supabase Error Response: " + e.getResponseBodyAsString());
            showMessage("Server Error (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), true);
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleGoToLogin() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();

            Stage stage = (Stage) backToLoginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error returning to login screen.", true);
        }
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setTextFill(isError ? Color.RED : Color.GREEN);
    }
}