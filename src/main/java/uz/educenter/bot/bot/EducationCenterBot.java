package uz.educenter.bot.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.educenter.bot.config.ConfigLoader;
import uz.educenter.bot.handler.AdminHandler;
import uz.educenter.bot.handler.StudentHandler;
import uz.educenter.bot.service.AdminService;
import uz.educenter.bot.service.ApplicationService;
import uz.educenter.bot.service.CourseService;
import uz.educenter.bot.service.UserService;
import uz.educenter.bot.state.SessionManager;

public class EducationCenterBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;

    private final AdminHandler adminHandler;
    private final StudentHandler studentHandler;

    public EducationCenterBot() {
        this.botUsername = ConfigLoader.get("bot.username");
        this.botToken = ConfigLoader.get("bot.token");

        CourseService courseService = new CourseService();
        UserService userService = new UserService();
        AdminService adminService = new AdminService();
        ApplicationService applicationService = new ApplicationService();
        SessionManager sessionManager = new SessionManager();

        BotMessageService botMessageService = new BotMessageService(this);

        this.adminHandler = new AdminHandler(
                courseService,
                userService,
                adminService,
                applicationService,
                sessionManager,
                botMessageService
        );

        this.studentHandler = new StudentHandler(
                courseService,
                userService,
                applicationService,
                sessionManager,
                botMessageService
        );
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                if (adminHandler.handleCallback(update.getCallbackQuery())) {
                    return;
                }

                studentHandler.handleCallback(update.getCallbackQuery());
                return;
            }

            if (update.hasMessage()) {
                Message message = update.getMessage();

                if (message.hasContact()) {
                    studentHandler.handleContactMessage(message);
                    return;
                }

                if (message.hasText()) {
                    if (adminHandler.handleTextMessage(message)) {
                        return;
                    }

                    studentHandler.handleTextMessage(message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}