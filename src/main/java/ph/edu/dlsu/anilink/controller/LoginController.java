package ph.edu.dlsu.anilink.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.Administrator;
import ph.edu.dlsu.anilink.model.Driver;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

/**
 * JavaFX controller for the user login view.
 * <p>
 * This class handles user authentication by capturing login credentials from the UI
 * and validating them against the database via {@link SupabaseService}. Upon a
 * successful login, it establishes a user session using {@link UserSession} and
 * dynamically routes the user to their role-specific dashboard (Admin, Driver,
 * or Passenger) based on their {@link User} subclass utilizing the {@link ViewNavigator}.
 * It also provides navigation to the registration view.
 * </p>
 */
@Controller
public class LoginController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    public LoginController(SupabaseService supabaseService, UserSession userSession, ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showMessage("Please fill in all fields.", true);
            return;
        }

        try {
            User user = supabaseService.findUserByEmail(email);

            if (user != null && user.getPassword().equals(password)) {
                userSession.setCurrentUser(user);

                // Use 'instanceof' to check exactly which type of User object was returned
                String fxmlPath;
                if (user instanceof Administrator) {
                    fxmlPath = "/fxml/AdminDashboard.fxml";
                } else if (user instanceof Driver) {
                    fxmlPath = "/fxml/DriverDashboard.fxml";
                } else {
                    // Default to passenger if it is an instance of Passenger or fallback
                    fxmlPath = "/fxml/PassengerDashboard.fxml";
                }

                viewNavigator.navigateTo(event, fxmlPath, 1000, 650);
            } else {
                showMessage("Invalid email or password.", true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Login error occurred.", true);
        }
    }

    @FXML
    private void handleGoToRegister(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/Register.fxml", 900, 600);
    }

    public void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setTextFill(isError ? Color.RED : Color.GREEN);
    }
}