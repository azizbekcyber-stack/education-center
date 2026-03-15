package uz.educenter.bot.bot;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;

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
    private static final String BTN_CANCEL = "❌ Bekor qilish";
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

            if (update.hasMessage()) {
                if (update.getMessage().hasContact()) {
                    handleContactMessage(update.getMessage());
                    return;
                }

                if (update.getMessage().hasText()) {
                    handleTextMessage(update.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = message.getFrom();
        Long telegramId = telegramUser.getId();
        String text = message.getText().trim();

        if ("/start".equals(text)) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);
            sendMainMenu(chatId);
            return;
        }

        if (BTN_MAIN_MENU.equals(text)) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);
            sendMainMenu(chatId);
            return;
        }

        if ("/cancel".equals(text) || BTN_CANCEL.equals(text)) {
            if (isApplicationFlowActive(telegramId)) {
                sessionManager.clearUserState(telegramId);
                sessionManager.clearPendingApplication(telegramId);
                sendMessage(chatId, "Jarayon bekor qilindi. ✅", KeyboardUtil.mainMenuKeyboard());
            } else {
                sendMessage(chatId, "Hozir bekor qilinadigan faol jarayon yo‘q.", KeyboardUtil.mainMenuKeyboard());
            }
            return;
        }

        UserState currentState = sessionManager.getUserState(telegramId);

        if (isApplicationInputState(currentState) && isBlockedDuringApplicationFlow(text)) {
            sendMessage(
                    chatId,
                    "Siz hozir zayavka jarayonidasiz. Davom eting yoki ❌ Bekor qilish ni bosing.",
                    currentState == UserState.WAITING_APPLICATION_PHONE
                            ? KeyboardUtil.phoneRequestKeyboardWithCancel()
                            : KeyboardUtil.cancelKeyboard()
            );
            return;
        }

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
                sendMessage(chatId, "Admin session yopildi. ✅", KeyboardUtil.mainMenuKeyboard());
                return;
            }
        }

        switch (text) {
            case BTN_COURSES -> showCourses(chatId);
            case BTN_PRICES -> showPrices(chatId);
            case BTN_LOCATION -> showLocation(chatId);
            case BTN_CONTACT -> showContacts(chatId);
            case BTN_APPLY -> {
                sendMessage(chatId, "Zayavka uchun avval kursni tanlang \uD83D\uDC47:");
                showCourses(chatId);
            }
            default -> sendMessage(chatId, "Kerakli bo‘limni tugma orqali tanlang. \uD83D\uDC47", KeyboardUtil.mainMenuKeyboard());
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();

        if (data.startsWith("course:")) {
            Long courseId = Long.parseLong(data.split(":")[1]);
            showCourseDetails(chatId, courseId);
            answerCallback(callbackQuery.getId(), "Kurs tanlandi ✅");
            return;
        }

        if (data.startsWith("group:")) {
            String[] parts = data.split(":");
            Long courseId = Long.parseLong(parts[1]);
            Long groupId = Long.parseLong(parts[2]);

            sessionManager.clearUserState(telegramId);
            sessionManager.createPendingApplication(telegramId);

            PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
            pendingApplication.setCourseId(courseId);
            pendingApplication.setCourseGroupId(groupId);

            sendMessage(
                    chatId,
                    "‼\uFE0F Siz haqiqatan ham zayavka qoldirmoqchimisiz?",
                    KeyboardUtil.applicationConfirmationKeyboard()
            );

            answerCallback(callbackQuery.getId(), "Guruh tanlandi 🎉");
            return;
        }

        if (data.startsWith("apply_confirm:")) {
            String decision = data.split(":")[1];

            if (callbackQuery.getMessage() != null) {
                clearInlineKeyboard(
                        chatId,
                        callbackQuery.getMessage().getMessageId()
                );
            }

            if ("yes".equals(decision)) {
                PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);

                if (pendingApplication == null) {
                    answerCallback(callbackQuery.getId(), "Jarayon topilmadi");
                    sendMessage(chatId, "Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
                    return;
                }

                sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_FULL_NAME);
                answerCallback(callbackQuery.getId(), "Davom etamiz ✅");
                sendMessage(chatId, "Ism-familyangizni kiriting: ⌨", KeyboardUtil.cancelKeyboard());
                return;
            }

            if ("no".equals(decision)) {
                sessionManager.clearPendingApplication(telegramId);
                sessionManager.clearUserState(telegramId);
                answerCallback(callbackQuery.getId(), "Bekor qilindi");
                return;
            }
        }

        if (data.startsWith("app_viewed:")) {
            if (!sessionManager.isAdminAuthenticated(telegramId)) {
                answerCallback(callbackQuery.getId(), "\uD83D\uDEA8 Ruxsat yo‘q ");
                return;
            }

            Long applicationId = Long.parseLong(data.split(":")[1]);
            boolean updated = applicationService.markAsViewed(applicationId);

            if (updated) {
                answerCallback(callbackQuery.getId(), "VIEWED qilindi \uD83D\uDC4C");

                if (callbackQuery.getMessage() != null) {
                    clearInlineKeyboard(
                            chatId,
                            callbackQuery.getMessage().getMessageId()
                    );
                }

                sendMessage(chatId, "Zayavka #" + applicationId + " VIEWED qilindi. \uD83D\uDC4C");
            } else {
                answerCallback(callbackQuery.getId(), "\uD83D\uDEA8 Xatolik yuz berdi ");
            }

            return;
        }
    }

    private void handleAdminEntry(Long chatId, Long telegramId) {
        if (!adminService.isAllowedAdmin(telegramId)) {
            sendMessage(chatId, "Siz admin sifatida ro‘yxatdan o‘tmagansiz. ❌", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_PASSWORD);
        sendMessage(chatId, "Admin parolini kiriting: \uD83D\uDD11");
    }

    private void handleAdminPassword(Long chatId, Long telegramId, String password) {
        boolean authenticated = adminService.authenticate(telegramId, password);

        if (!authenticated) {
            sendMessage(chatId, "❌ Parol noto‘g‘ri. Qayta urinib ko‘ring:");
            return;
        }

        sessionManager.clearUserState(telegramId);
        sessionManager.authenticateAdmin(telegramId);
        sendMessage(chatId, "\uD83E\uDD1D Admin panelga xush kelibsiz.", KeyboardUtil.adminMenuKeyboard());
    }

    private void handleApplicationFullName(Long chatId, Long telegramId, String fullName) {
        if (fullName.isBlank()) {
            sendMessage(chatId, "❌ Ism-familya bo‘sh bo‘lmasligi kerak. Qayta kiriting:");
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            sendMessage(chatId, "\uD83D\uDEA8 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setFullName(fullName);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_PHONE);
        sendMessage(
                chatId,
                "Telefon raqamingizni yuboring.\n \uD83D\uDCDE Pastdagi tugmani bosing yoki qo‘lda kiriting.\nMasalan: +998901234567",
                KeyboardUtil.phoneRequestKeyboardWithCancel()
        );
    }

    private void handleApplicationPhone(Long chatId, Long telegramId, String phone) {
        String normalizedPhone = normalizePhone(phone);

        if (!isValidPhone(normalizedPhone)) {
            sendMessage(
                    chatId,
                    "❌ Telefon format noto‘g‘ri. Pastdagi tugma orqali yuboring yoki to‘g‘ri formatda kiriting.\nMasalan: +998901234567",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            sendMessage(chatId, "\uD83D\uDEA8 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setPhone(normalizedPhone);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_MESSAGE);
        sendMessage(chatId, "\uD83D\uDCAC Qo‘shimcha izoh yozing. Agar izoh bo‘lmasa, - yuboring:", KeyboardUtil.removeKeyboard());
    }


    private void handleContactMessage(Message message) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        if (sessionManager.getUserState(telegramId) != UserState.WAITING_APPLICATION_PHONE) {
            sendMessage(chatId, "\uD83D\uDEA8 Hozir telefon raqami so‘ralmagan.", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        Contact contact = message.getContact();
        if (contact == null || contact.getPhoneNumber() == null || contact.getPhoneNumber().isBlank()) {
            sendMessage(
                    chatId,
                    "\uD83D\uDEA8 Telefon raqamini olishning imkoni bo‘lmadi. Qayta urinib ko‘ring yoki qo‘lda kiriting.",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return;
        }

        if (contact.getUserId() != null && !telegramId.equals(contact.getUserId())) {
            sendMessage(
                    chatId,
                    "\uD83D\uDE4F Iltimos, aynan o‘zingizning raqamingizni yuboring.",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return;
        }

        handleApplicationPhone(chatId, telegramId, contact.getPhoneNumber());
    }



    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }

        String normalized = phone.trim();

        if (normalized.startsWith("+")) {
            normalized = "+" + normalized.substring(1).replaceAll("\\D", "");
        } else {
            normalized = normalized.replaceAll("\\D", "");

            if (normalized.startsWith("00")) {
                normalized = "+" + normalized.substring(2);
            } else if (!normalized.isBlank()) {
                normalized = "+" + normalized;
            }
        }

        return normalized;
    }
    private void handleApplicationMessage(Long chatId, Long telegramId, org.telegram.telegrambots.meta.api.objects.User telegramUser, String messageText) {
        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);

        if (pendingApplication == null) {
            sendMessage(chatId, "\uD83D\uDEA8 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
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
                    "✅ Zayavkangiz qabul qilindi.\nAriza ID: " + savedApplication.getId() + "\nSizga tez orada aloqaga chiqamiz. Agar kutishni istamasangiz istalgan vaqt ☎\uFE0F Aloqa bo'limi orqali bizga bog'lanishingiz mumkin.",
                    KeyboardUtil.mainMenuKeyboard()
            );
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Zayavkani saqlashda xatolik bo‘ldi.");
        }
    }

    private void showCourses(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            sendMessage(chatId, "Hozircha aktiv kurslar yo‘q.❌");
            return;
        }

        sendMessage(chatId, "Kurslardan birini tanlang: \uD83D\uDC47", KeyboardUtil.coursesKeyboard(courses));
    }

    private void showCourseDetails(Long chatId, Long courseId) {
        Course course = courseService.getCourseById(courseId);

        if (course == null) {
            sendMessage(chatId, "Kurs topilmadi. ❌");
            return;
        }

        List<CourseGroup> groups = courseService.getActiveGroupsByCourseId(courseId);

        StringBuilder text = new StringBuilder();
        text.append("📘 <b>").append(escapeHtml(course.getName())).append("</b>\n\n");

        if (course.getDescription() != null && !course.getDescription().isBlank()) {
            text.append("<i>")
                    .append(escapeHtml(course.getDescription()))
                    .append("</i>\n\n");
        }

        text.append("💰 <b>Narxi:</b> ")
                .append(formatPrice(course.getPrice()))
                .append("\n");

        text.append("⏳ <b>Davomiyligi:</b> ")
                .append(escapeHtml(course.getCourseDuration()))
                .append("\n");

        text.append("📡 <b>Format:</b> ")
                .append(formatCourseType(course.getCourseType()))
                .append("\n\n");

        if (groups.isEmpty()) {
            text.append("⚠️ Hozircha aktiv guruhlar yo'q.");
            sendMessage(chatId, text.toString());
            return;
        }

        text.append("👥 <b>Mavjud guruhlar:</b>\n");

        for (CourseGroup group : groups) {
            text.append("\n")
                    .append("<b>").append(escapeHtml(group.getGroupName())).append("</b>\n")
                    .append("🗓 <b>Kunlar:</b> ").append(escapeHtml(shortDays(group.getDaysText()))).append("\n")
                    .append("🕒 <b>Vaqt:</b> ").append(group.getStartTime()).append(" - ").append(group.getEndTime()).append("\n")
                    .append("📅 <b>Muddat:</b> ").append(formatDate(group.getStartDate()))
                    .append(" - ").append(formatDate(group.getEndDate())).append("\n");
        }

        text.append("\nKerakli guruhni tanlang:");

        sendMessage(chatId, text.toString(), KeyboardUtil.courseDetailsKeyboard(course, groups));
    }

    private void showPrices(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            sendMessage(chatId, "❌ Hozircha aktiv kurslar topilmadi.", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        sendMessage(chatId, buildPricesMessage(courses), KeyboardUtil.mainMenuKeyboard());
    }


    private String buildPricesMessage(List<Course> courses) {
        StringBuilder text = new StringBuilder();

        text.append("💰 <b>Kurs narxlari</b>\n\n");
        text.append("Quyida markazimizdagi faol kurslar narxlari keltirilgan:\n\n");

        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);

            text.append("<b>")
                    .append(i + 1)
                    .append(". ")
                    .append(escapeHtml(course.getName()))
                    .append("</b>\n");

            text.append("📡 <b>Format:</b> ")
                    .append(formatCourseType(course.getCourseType()))
                    .append("\n");

            text.append("⏳ <b>Davomiyligi:</b> ")
                    .append(course.getCourseDuration() == null || course.getCourseDuration().isBlank()
                            ? "-"
                            : escapeHtml(course.getCourseDuration()))
                    .append("\n");

            text.append("💵 <b>Narxi:</b> ")
                    .append(formatPrice(course.getPrice()))
                    .append("\n");

            if (i < courses.size() - 1) {
                text.append("\n");
            }
        }

        text.append("\n<i>To‘liq ma’lumot va guruh tanlash uchun “📚 Kurslar” bo‘limidan foydalaning.</i>");

        return text.toString();
    }

    private void showLocation(Long chatId) {
        String address = ConfigLoader.get("center.address");
        String locationUrl = ConfigLoader.get("center.location_url");

        String text = "📍 Manzil:\n" + escapeHtml(address) + "\n\n🔗 Lokatsiya:\n" + escapeHtml(locationUrl);
        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private void showContacts(Long chatId) {
        String teacher1 = ConfigLoader.get("teacher1.username");
        String teacher2 = ConfigLoader.get("teacher2.username");

        String text = """
            ☎️ Ustozlar bilan aloqa:
            
            1. 👨🏽‍🏫 %s
            2. 👨🏽‍🏫 %s
            """.formatted(
                escapeHtml(teacher1),
                escapeHtml(teacher2)
        );

        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private void showApplications(Long chatId, boolean onlyNew) {
        List<Application> applications = onlyNew
                ? applicationService.getApplicationsByStatus(ApplicationStatus.NEW)
                : applicationService.getAllApplications();

        if (applications.isEmpty()) {
            String emptyMessage = onlyNew
                    ? "❌ Yangi zayavkalar topilmadi."
                    : "❌ Zayavkalar topilmadi.";

            sendMessage(chatId, emptyMessage, KeyboardUtil.adminMenuKeyboard());
            return;
        }

        for (Application application : applications) {
            Course course = courseService.getCourseById(application.getCourseId());
            CourseGroup group = courseService.getCourseGroupById(application.getCourseGroupId());
            uz.educenter.bot.model.User user = userService.findById(application.getUserId());

            String courseName = course != null
                    ? escapeHtml(course.getName())
                    : String.valueOf(application.getCourseId());

            String groupName = group != null
                    ? escapeHtml(group.getGroupName())
                    : String.valueOf(application.getCourseGroupId());

            String applicationFullName = application.getFullName() == null || application.getFullName().isBlank()
                    ? "-"
                    : escapeHtml(application.getFullName());

            String applicationPhone = application.getPhone() == null || application.getPhone().isBlank()
                    ? "-"
                    : escapeHtml(application.getPhone());

            String applicationMessage = application.getMessage() == null || application.getMessage().isBlank()
                    ? "-"
                    : escapeHtml(application.getMessage());

            StringBuilder text = new StringBuilder();
            text.append("🆔 Ariza ID: ").append(application.getId()).append("\n");
            text.append("👤 Ism: ").append(applicationFullName).append("\n");
            text.append("🔗 Telegram: ").append(formatTelegramUsername(user)).append("\n");
            text.append("📞 Telefon: ").append(applicationPhone).append("\n");
            text.append("📚 Kurs: ").append(courseName).append("\n");
            text.append("👥 Guruh: ").append(groupName).append("\n");
            text.append("💬 Izoh: ").append(applicationMessage).append("\n");
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
👋 Assalomu alaykum! Xush kelibsiz!

Bizning xizmatimizdan foydalanish uchun quyidagi bo‘limlardan birini tanlang: 
                """;
        sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        return phone.matches("^\\+\\d{9,15}$");
    }

    private String buildTelegramName(org.telegram.telegrambots.meta.api.objects.User user){
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
        message.setParseMode("HTML");

        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String formatPrice(java.math.BigDecimal price) {
        if (price == null) {
            return "-";
        }

        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');

        java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#,###", symbols);
        decimalFormat.setGroupingUsed(true);
        decimalFormat.setMaximumFractionDigits(0);

        return decimalFormat.format(price) + " so'm";
    }

    private String formatDate(java.time.LocalDate date) {
        if (date == null) {
            return "-";
        }

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

        return date.format(formatter);
    }

    private String formatCourseType(String courseType) {
        if (courseType == null || courseType.isBlank()) {
            return "Noma'lum";
        }

        return switch (courseType.trim().toUpperCase()) {
            case "ONLINE" -> "🌐 Online";
            case "OFFLINE" -> "🏫 Offline";
            default -> courseType;
        };
    }

    private String shortDays(String daysText) {
        if (daysText == null || daysText.isBlank()) {
            return "-";
        }

        return daysText
                .replace("Dushanba", "Dush")
                .replace("Seshanba", "Sesh")
                .replace("Chorshanba", "Chor")
                .replace("Payshanba", "Pay")
                .replace("Juma", "Juma")
                .replace("Shanba", "Shan")
                .replace("Yakshanba", "Yak");
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String formatTelegramUsername(uz.educenter.bot.model.User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "-";
        }

        return "@" + escapeHtml(user.getUsername());
    }


    private void clearInlineKeyboard(Long chatId, Integer messageId) {
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(chatId.toString());
        editMessageReplyMarkup.setMessageId(messageId);
        editMessageReplyMarkup.setReplyMarkup(null);

        try {
            execute(editMessageReplyMarkup);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean isApplicationFlowActive(Long telegramId) {
        UserState state = sessionManager.getUserState(telegramId);
        return state == UserState.WAITING_APPLICATION_FULL_NAME
                || state == UserState.WAITING_APPLICATION_PHONE
                || state == UserState.WAITING_APPLICATION_MESSAGE
                || sessionManager.getPendingApplication(telegramId) != null;
    }

    private boolean isApplicationInputState(UserState state) {
        return state == UserState.WAITING_APPLICATION_FULL_NAME
                || state == UserState.WAITING_APPLICATION_PHONE
                || state == UserState.WAITING_APPLICATION_MESSAGE;
    }

    private boolean isBlockedDuringApplicationFlow(String text) {
        return BTN_COURSES.equals(text)
                || BTN_PRICES.equals(text)
                || BTN_LOCATION.equals(text)
                || BTN_CONTACT.equals(text)
                || BTN_APPLY.equals(text)
                || BTN_ADMIN.equals(text)
                || BTN_NEW_APPLICATIONS.equals(text)
                || BTN_ALL_APPLICATIONS.equals(text)
                || BTN_ADMIN_LOGOUT.equals(text)
                || BTN_MAIN_MENU.equals(text)
                || "/admin".equals(text);
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