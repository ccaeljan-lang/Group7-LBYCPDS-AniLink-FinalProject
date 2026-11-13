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

    private boolean isRegisterSuccess;

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
        isRegisterSuccess = false;
    }

    @FXML
    private void handleRegister() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String selection = categoryComboBox.getValue();
        String password = passwordField.getText();

        // Required fields
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || selection == null) {
            showMessage("Please fill in all required fields.", true);
            return;
        }

        // DLSU email format check (@dlsu.edu.ph)
        if (!email.toLowerCase().endsWith("@dlsu.edu.ph")) {
            showMessage("Please use a valid DLSU email address (@dlsu.edu.ph).", true);
            return;
        }

        // SECURITY GAP (documented): A @dlsu.edu.ph suffix check only validates
        // the domain format. It does NOT verify email ownership or EAF. A real
        // confirmation requires a properly configured and tested email/auth
        // provider (e.g., an email confirmation link). Do not claim actual
        // email verification here without such a provider.

        // Basic password rules
        if (!isValidPassword(password)) {
            return;
        }

        // Prevent duplicate email registration (check Supabase before insert).
        try {
            if (supabaseService.emailExists(email)) {
                showMessage("An account with this email already exists.", true);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error checking email: " + e.getMessage(), true);
            return;
        }

        // Determine the actual database Role based on the dropdown selection.
        // STUDENT and FACULTY both map to ONE PASSENGER category.
        String dbRole;
        if ("ADMIN".equals(selection)) {
            dbRole = "ADMINISTRATOR";
        } else if ("DRIVER".equals(selection)) {
            dbRole = "DRIVER";
        } else {
            dbRole = "PASSENGER"; // STUDENT/FACULTY defaults to PASSENGER
        }

        try {
            // SECURITY RISK (documented): passwords are sent/stored in
            // plaintext. Hash passwords (e.g., bcrypt) before persistence in
            // a production system.
            supabaseService.registerAccount(name, email, password, dbRole, selection);
            showMessage("Account created successfully!", false);
            isRegisterSuccess = true;
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

    /**
     * Basic password validation rule.
     *
     * @return true if the password meets the minimum rule, false otherwise
     *         (an error message is shown to the user).
     */
    private boolean isValidPassword(String password) {
        if (password.length() < 6) {
            showMessage("Password must be at least 6 characters long.", true);
            return false;
        }
        return true;
    }

    @FXML
    private void handleGoToLogin() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();

            Stage stage = (Stage) backToLoginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));

            // Display successful registration in login view
            if (isRegisterSuccess) {
                LoginController login = fxmlLoader.getController();
                login.showMessage("Account created successfully!", false);
            }

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