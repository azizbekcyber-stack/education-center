package uz.educenter.bot.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import uz.educenter.bot.config.ConfigLoader;
import uz.educenter.bot.model.Application;
import uz.educenter.bot.model.ApplicationStatus;
import uz.educenter.bot.model.Course;
import uz.educenter.bot.model.CourseGroup;
import uz.educenter.bot.state.PendingApplication;
import uz.educenter.bot.state.SessionManager;
import uz.educenter.bot.state.UserState;
import uz.educenter.bot.service.AdminService;
import uz.educenter.bot.service.ApplicationService;
import uz.educenter.bot.service.CourseService;
import uz.educenter.bot.service.UserService;
import uz.educenter.bot.util.KeyboardUtil;

import java.util.List;

public class EducationCenterBot extends TelegramLongPollingBot {

    private static final String BTN_COURSES = "📚 Kurslar";
    private static final String BTN_PRICES = "💰 Narxlar";
    private static final String BTN_LOCATION = "📍 Manzil";
    private static final String BTN_CONTACT = "☎️ Aloqa";
    private static final String BTN_APPLY = "📝 Zayavka qoldirish";
    private static final String BTN_ADMIN = "🔐 Admin";

    private static final String BTN_NEW_APPLICATIONS = "🆕 Yangi zayavkalar";
    private static final String BTN_ALL_APPLICATIONS = "📋 Barcha zayavkalar";
    private static final String BTN_ADMIN_LOGOUT = "🚪 Admin chiqish";
    private static final String BTN_MAIN_MENU = "🏠 Bosh menu";

    private final String botUsername;
    private final String botToken;

    private final CourseService courseService;
    private final UserService userService;
    private final AdminService adminService;
    private final ApplicationService applicationService;
    private final SessionManager sessionManager;

    public EducationCenterBot() {
        this.botUsername = ConfigLoader.get("bot.username");
        this.botToken = ConfigLoader.get("bot.token");

        this.courseService = new CourseService();
        this.userService = new UserService();
        this.adminService = new AdminService();
        this.applicationService = new ApplicationService();
        this.sessionManager = new SessionManager();
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
                handleCallback(update.getCallbackQuery());
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        User telegramUser = message.getFrom();
        Long telegramId = telegramUser.getId();
        String text = message.getText().trim();

        if ("/start".equals(text)) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);
            sendMainMenu(chatId);
            return;
        }

        if (BTN_MAIN_MENU.equals(text)) {
            sendMainMenu(chatId);
            return;
        }

        UserState currentState = sessionManager.getUserState(telegramId);

        if (currentState == UserState.WAITING_ADMIN_PASSWORD) {
            handleAdminPassword(chatId, telegramId, text);
            return;
        }

        if (currentState == UserState.WAITING_APPLICATION_FULL_NAME) {
            handleApplicationFullName(chatId, telegramId, text);
            return;
        }

        if (currentState == UserState.WAITING_APPLICATION_PHONE) {
            handleApplicationPhone(chatId, telegramId, text);
            return;
        }

        if (currentState == UserState.WAITING_APPLICATION_MESSAGE) {
            handleApplicationMessage(chatId, telegramId, telegramUser, text);
            return;
        }

        if ("/admin".equals(text) || BTN_ADMIN.equals(text)) {
            handleAdminEntry(chatId, telegramId);
            return;
        }

        if (sessionManager.isAdminAuthenticated(telegramId)) {
            if (BTN_NEW_APPLICATIONS.equals(text)) {
                showApplications(chatId, true);
                return;
            }

            if (BTN_ALL_APPLICATIONS.equals(text)) {
                showApplications(chatId, false);
                return;
            }

            if (BTN_ADMIN_LOGOUT.equals(text)) {
                sessionManager.logoutAdmin(telegramId);
                sendMessage(chatId, "Admin session yopildi.", KeyboardUtil.mainMenuKeyboard());
                return;
            }
        }

        switch (text) {
            case BTN_COURSES -> showCourses(chatId);
            case BTN_PRICES -> showPrices(chatId);
            case BTN_LOCATION -> showLocation(chatId);
            case BTN_CONTACT -> showContacts(chatId);
            case BTN_APPLY -> {
                sendMessage(chatId, "Zayavka uchun avval kursni tanlang:");
                showCourses(chatId);
            }
            default -> sendMessage(chatId, "Kerakli bo‘limni tugma orqali tanlang.", KeyboardUtil.mainMenuKeyboard());
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();

        if (data.startsWith("course:")) {
            Long courseId = Long.parseLong(data.split(":")[1]);
            showCourseDetails(chatId, courseId);
            answerCallback(callbackQuery.getId(), "Kurs tanlandi");
            return;
        }

        if (data.startsWith("group:")) {
            String[] parts = data.split(":");
            Long courseId = Long.parseLong(parts[1]);
            Long groupId = Long.parseLong(parts[2]);

            sessionManager.createPendingApplication(telegramId);
            PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
            pendingApplication.setCourseId(courseId);
            pendingApplication.setCourseGroupId(groupId);

            sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_FULL_NAME);
            sendMessage(chatId, "Ism-familyangizni kiriting:");
            answerCallback(callbackQuery.getId(), "Guruh tanlandi");
            return;
        }

        if (data.startsWith("app_viewed:")) {
            if (!sessionManager.isAdminAuthenticated(telegramId)) {
                answerCallback(callbackQuery.getId(), "Ruxsat yo‘q");
                return;
            }

            Long applicationId = Long.parseLong(data.split(":")[1]);
            boolean updated = applicationService.markAsViewed(applicationId);

            if (updated) {
                answerCallback(callbackQuery.getId(), "VIEWED qilindi");
                sendMessage(chatId, "Zayavka #" + applicationId + " VIEWED qilindi.");
            } else {
                answerCallback(callbackQuery.getId(), "Xatolik yuz berdi");
            }
        }
    }

    private void handleAdminEntry(Long chatId, Long telegramId) {
        if (!adminService.isAllowedAdmin(telegramId)) {
            sendMessage(chatId, "Siz admin sifatida ro‘yxatdan o‘tmagansiz.", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_PASSWORD);
        sendMessage(chatId, "Admin parolini kiriting:");
    }

    private void handleAdminPassword(Long chatId, Long telegramId, String password) {
        boolean authenticated = adminService.authenticate(telegramId, password);

        if (!authenticated) {
            sendMessage(chatId, "Parol noto‘g‘ri. Qayta urinib ko‘ring:");
            return;
        }

        sessionManager.clearUserState(telegramId);
        sessionManager.authenticateAdmin(telegramId);
        sendMessage(chatId, "Admin panelga xush kelibsiz.", KeyboardUtil.adminMenuKeyboard());
    }

    private void handleApplicationFullName(Long chatId, Long telegramId, String fullName) {
        if (fullName.isBlank()) {
            sendMessage(chatId, "Ism-familya bo‘sh bo‘lmasligi kerak. Qayta kiriting:");
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            sendMessage(chatId, "Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setFullName(fullName);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_PHONE);
        sendMessage(chatId, "Telefon raqamingizni kiriting. Masalan: +998901234567");
    }

    private void handleApplicationPhone(Long chatId, Long telegramId, String phone) {
        if (!isValidPhone(phone)) {
            sendMessage(chatId, "Telefon format noto‘g‘ri. Masalan: +998901234567");
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            sendMessage(chatId, "Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setPhone(phone);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_MESSAGE);
        sendMessage(chatId, "Qo‘shimcha izoh yozing. Agar izoh bo‘lmasa, - yuboring:");
    }

    private void handleApplicationMessage(Long chatId, Long telegramId, User telegramUser, String messageText) {
        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);

        if (pendingApplication == null) {
            sendMessage(chatId, "Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        String fullNameFromTelegram = buildTelegramName(telegramUser);
        uz.educenter.bot.model.User user = userService.getOrCreateUser(
                telegramId,
                fullNameFromTelegram,
                telegramUser.getUserName()
        );

        userService.updatePhone(user.getId(), pendingApplication.getPhone());

        Application application = new Application();
        application.setUserId(user.getId());
        application.setCourseId(pendingApplication.getCourseId());
        application.setCourseGroupId(pendingApplication.getCourseGroupId());
        application.setFullName(pendingApplication.getFullName());
        application.setPhone(pendingApplication.getPhone());
        application.setMessage("-".equals(messageText) ? null : messageText);
        application.setStatus(ApplicationStatus.NEW);

        try {
            Application savedApplication = applicationService.createApplication(application);

            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);

            sendMessage(
                    chatId,
                    "Zayavkangiz qabul qilindi.\nAriza ID: " + savedApplication.getId(),
                    KeyboardUtil.mainMenuKeyboard()
            );
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "Zayavkani saqlashda xatolik bo‘ldi.");
        }
    }

    private void showCourses(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            sendMessage(chatId, "Hozircha aktiv kurslar yo‘q.");
            return;
        }

        sendMessage(chatId, "Kurslardan birini tanlang:", KeyboardUtil.coursesKeyboard(courses));
    }

    private void showCourseDetails(Long chatId, Long courseId) {
        Course course = courseService.getCourseById(courseId);

        if (course == null) {
            sendMessage(chatId, "Kurs topilmadi.");
            return;
        }

        List<CourseGroup> groups = courseService.getActiveGroupsByCourseId(courseId);

        StringBuilder text = new StringBuilder();
        text.append("📘 ").append(course.getName()).append("\n\n");
        text.append("📝 ").append(course.getDescription()).append("\n");
        text.append("💰 Narxi: ").append(course.getPrice().toPlainString()).append("\n");
        text.append("⏳ Davomiyligi: ").append(course.getCourseDuration()).append("\n\n");

        if (groups.isEmpty()) {
            text.append("Hozircha aktiv guruhlar yo‘q.");
            sendMessage(chatId, text.toString());
            return;
        }

        text.append("Mavjud guruhlar:\n");
        for (CourseGroup group : groups) {
            text.append("\n")
                    .append("• ").append(group.getGroupName()).append("\n")
                    .append("  Kunlar: ").append(group.getDaysText()).append("\n")
                    .append("  Vaqt: ").append(group.getStartTime()).append(" - ").append(group.getEndTime()).append("\n")
                    .append("  Sana: ").append(group.getStartDate()).append(" dan ").append(group.getEndDate()).append(" gacha\n");
        }

        text.append("\nZayavka uchun kerakli guruhni tanlang.");

        sendMessage(chatId, text.toString(), KeyboardUtil.courseGroupsKeyboard(courseId, groups));
    }

    private void showPrices(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            sendMessage(chatId, "Kurslar topilmadi.");
            return;
        }

        StringBuilder text = new StringBuilder("💰 Kurs narxlari:\n\n");
        for (Course course : courses) {
            text.append("• ")
                    .append(course.getName())
                    .append(" — ")
                    .append(course.getPrice().toPlainString())
                    .append("\n");
        }

        sendMessage(chatId, text.toString(), KeyboardUtil.mainMenuKeyboard());
    }

    private void showLocation(Long chatId) {
        String address = ConfigLoader.get("center.address");
        String locationUrl = ConfigLoader.get("center.location_url");

        String text = "📍 Manzil:\n" + address + "\n\n🔗 Lokatsiya:\n" + locationUrl;
        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private void showContacts(Long chatId) {
        String teacher1 = ConfigLoader.get("teacher1.username");
        String teacher2 = ConfigLoader.get("teacher2.username");

        String text = """
                ☎️ Ustozlar bilan aloqa:
                
                1. %s
                2. %s
                """.formatted(teacher1, teacher2);

        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private void showApplications(Long chatId, boolean onlyNew) {
        List<Application> applications = onlyNew
                ? applicationService.getApplicationsByStatus(ApplicationStatus.NEW)
                : applicationService.getAllApplications();

        if (applications.isEmpty()) {
            sendMessage(chatId, "Zayavkalar topilmadi.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        for (Application application : applications) {
            Course course = courseService.getCourseById(application.getCourseId());
            CourseGroup group = courseService.getCourseGroupById(application.getCourseGroupId());

            StringBuilder text = new StringBuilder();
            text.append("🆔 Ariza ID: ").append(application.getId()).append("\n");
            text.append("👤 Ism: ").append(application.getFullName()).append("\n");
            text.append("📞 Telefon: ").append(application.getPhone()).append("\n");
            text.append("📚 Kurs: ").append(course != null ? course.getName() : application.getCourseId()).append("\n");
            text.append("👥 Guruh: ").append(group != null ? group.getGroupName() : application.getCourseGroupId()).append("\n");
            text.append("💬 Izoh: ").append(application.getMessage() == null ? "-" : application.getMessage()).append("\n");
            text.append("📌 Status: ").append(application.getStatus()).append("\n");
            text.append("🕒 Vaqt: ").append(application.getCreatedAt()).append("\n");

            if (application.getStatus() == ApplicationStatus.NEW) {
                sendMessage(chatId, text.toString(), KeyboardUtil.applicationActionsKeyboard(application.getId()));
            } else {
                sendMessage(chatId, text.toString());
            }
        }
    }

    private void sendMainMenu(Long chatId) {
        String text = """
                Assalomu alaykum.
                
                Kerakli bo‘limni tanlang:
                """;
        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private boolean isValidPhone(String phone) {
        String normalized = phone.replaceAll("\\s+", "");
        return normalized.matches("^\\+?\\d{9,15}$");
    }

    private String buildTelegramName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? "Telegram User" : fullName;
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void answerCallback(String callbackId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(text);

        try {
            execute(answer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}