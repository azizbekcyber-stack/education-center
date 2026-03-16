package uz.educenter.bot.handler;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.educenter.bot.bot.BotMessageService;
import uz.educenter.bot.config.ConfigLoader;
import uz.educenter.bot.model.Application;
import uz.educenter.bot.model.ApplicationStatus;
import uz.educenter.bot.model.Course;
import uz.educenter.bot.model.CourseGroup;
import uz.educenter.bot.service.ApplicationService;
import uz.educenter.bot.service.CourseService;
import uz.educenter.bot.service.UserService;
import uz.educenter.bot.state.PendingApplication;
import uz.educenter.bot.state.SessionManager;
import uz.educenter.bot.state.UserState;
import uz.educenter.bot.util.KeyboardUtil;

import java.util.List;

public class StudentHandler {

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
    private static final String BTN_ADD_GROUP = "➕ Yangi guruh qo‘shish";

    private final CourseService courseService;
    private final UserService userService;
    private final ApplicationService applicationService;
    private final SessionManager sessionManager;
    private final BotMessageService botMessageService;

    public StudentHandler(
            CourseService courseService,
            UserService userService,
            ApplicationService applicationService,
            SessionManager sessionManager,
            BotMessageService botMessageService
    ) {
        this.courseService = courseService;
        this.userService = userService;
        this.applicationService = applicationService;
        this.sessionManager = sessionManager;
        this.botMessageService = botMessageService;
    }

    public boolean handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = message.getFrom();
        Long telegramId = telegramUser.getId();
        String text = message.getText().trim();

        if ("/start".equals(text)) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);
            sendMainMenu(chatId);
            return true;
        }

        if (BTN_MAIN_MENU.equals(text)) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingApplication(telegramId);
            sendMainMenu(chatId);
            return true;
        }

        if ("/cancel".equals(text) || BTN_CANCEL.equals(text)) {
            if (isApplicationFlowActive(telegramId)) {
                sessionManager.clearUserState(telegramId);
                sessionManager.clearPendingApplication(telegramId);
                botMessageService.sendMessage(chatId, "Jarayon bekor qilindi. ✅", KeyboardUtil.mainMenuKeyboard());
            } else {
                botMessageService.sendMessage(chatId, "Hozir bekor qilinadigan faol jarayon yo‘q.", KeyboardUtil.mainMenuKeyboard());
            }
            return true;
        }

        UserState currentState = sessionManager.getUserState(telegramId);

        if (isApplicationInputState(currentState) && isBlockedDuringApplicationFlow(text)) {
            botMessageService.sendMessage(
                    chatId,
                    "Siz hozir zayavka jarayonidasiz. Davom eting yoki ❌ Bekor qilish ni bosing.",
                    currentState == UserState.WAITING_APPLICATION_PHONE
                            ? KeyboardUtil.phoneRequestKeyboardWithCancel()
                            : KeyboardUtil.cancelKeyboard()
            );
            return true;
        }

        if (currentState == UserState.WAITING_APPLICATION_FULL_NAME) {
            handleApplicationFullName(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_APPLICATION_PHONE) {
            handleApplicationPhone(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_APPLICATION_MESSAGE) {
            handleApplicationMessage(chatId, telegramId, telegramUser, text);
            return true;
        }

        switch (text) {
            case BTN_COURSES -> showCourses(chatId);
            case BTN_PRICES -> showPrices(chatId);
            case BTN_LOCATION -> showLocation(chatId);
            case BTN_CONTACT -> showContacts(chatId);
            case BTN_APPLY -> {
                botMessageService.sendMessage(chatId, "Zayavka uchun avval kursni tanlang 👇:");
                showCourses(chatId);
            }
            default -> botMessageService.sendMessage(chatId, "Kerakli bo‘limni tugma orqali tanlang. 👇", KeyboardUtil.mainMenuKeyboard());
        }

        return true;
    }

    public boolean handleContactMessage(Message message) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();

        if (sessionManager.getUserState(telegramId) != UserState.WAITING_APPLICATION_PHONE) {
            botMessageService.sendMessage(chatId, "🚨 Hozir telefon raqami so‘ralmagan.", KeyboardUtil.mainMenuKeyboard());
            return true;
        }

        Contact contact = message.getContact();
        if (contact == null || contact.getPhoneNumber() == null || contact.getPhoneNumber().isBlank()) {
            botMessageService.sendMessage(
                    chatId,
                    "🚨 Telefon raqamini olishning imkoni bo‘lmadi. Qayta urinib ko‘ring yoki qo‘lda kiriting.",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return true;
        }

        if (contact.getUserId() != null && !telegramId.equals(contact.getUserId())) {
            botMessageService.sendMessage(
                    chatId,
                    "🙏 Iltimos, aynan o‘zingizning raqamingizni yuboring.",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return true;
        }

        handleApplicationPhone(chatId, telegramId, contact.getPhoneNumber());
        return true;
    }

    public boolean handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();

        if (data.startsWith("course:")) {
            Long courseId = Long.parseLong(data.split(":")[1]);
            showCourseDetails(chatId, courseId);
            botMessageService.answerCallback(callbackQuery.getId(), "Kurs tanlandi ✅");
            return true;
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

            botMessageService.sendMessage(
                    chatId,
                    "‼️ Siz haqiqatan ham zayavka qoldirmoqchimisiz?",
                    KeyboardUtil.applicationConfirmationKeyboard()
            );

            botMessageService.answerCallback(callbackQuery.getId(), "Guruh tanlandi 🎉");
            return true;
        }

        if (data.startsWith("apply_confirm:")) {
            String decision = data.split(":")[1];

            if (callbackQuery.getMessage() != null) {
                botMessageService.clearInlineKeyboard(chatId, callbackQuery.getMessage().getMessageId());
            }

            if ("yes".equals(decision)) {
                PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);

                if (pendingApplication == null) {
                    botMessageService.answerCallback(callbackQuery.getId(), "Jarayon topilmadi");
                    botMessageService.sendMessage(chatId, "Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
                    return true;
                }

                sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_FULL_NAME);
                botMessageService.answerCallback(callbackQuery.getId(), "Davom etamiz ✅");
                botMessageService.sendMessage(chatId, "Ism-familyangizni kiriting: ⌨", KeyboardUtil.cancelKeyboard());
                return true;
            }

            if ("no".equals(decision)) {
                sessionManager.clearPendingApplication(telegramId);
                sessionManager.clearUserState(telegramId);
                botMessageService.answerCallback(callbackQuery.getId(), "Bekor qilindi");
                return true;
            }
        }

        return false;
    }

    private void handleApplicationFullName(Long chatId, Long telegramId, String fullName) {
        if (fullName.isBlank()) {
            botMessageService.sendMessage(chatId, "❌ Ism-familya bo‘sh bo‘lmasligi kerak. Qayta kiriting:", KeyboardUtil.cancelKeyboard());
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            botMessageService.sendMessage(chatId, "🚨 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setFullName(fullName);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_PHONE);
        botMessageService.sendMessage(
                chatId,
                "Telefon raqamingizni yuboring.\n📞 Pastdagi tugmani bosing yoki qo‘lda kiriting.\nMasalan: +998901234567",
                KeyboardUtil.phoneRequestKeyboardWithCancel()
        );
    }

    private void handleApplicationPhone(Long chatId, Long telegramId, String phone) {
        String normalizedPhone = normalizePhone(phone);

        if (!isValidPhone(normalizedPhone)) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Telefon format noto‘g‘ri. Pastdagi tugma orqali yuboring yoki to‘g‘ri formatda kiriting.\nMasalan: +998901234567",
                    KeyboardUtil.phoneRequestKeyboardWithCancel()
            );
            return;
        }

        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);
        if (pendingApplication == null) {
            botMessageService.sendMessage(chatId, "🚨 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
            sessionManager.clearUserState(telegramId);
            return;
        }

        pendingApplication.setPhone(normalizedPhone);
        sessionManager.setUserState(telegramId, UserState.WAITING_APPLICATION_MESSAGE);
        botMessageService.sendMessage(chatId, "💬 Qo‘shimcha izoh yozing. Agar izoh bo‘lmasa, - yuboring:", KeyboardUtil.removeKeyboard());
    }

    private void handleApplicationMessage(
            Long chatId,
            Long telegramId,
            org.telegram.telegrambots.meta.api.objects.User telegramUser,
            String messageText
    ) {
        PendingApplication pendingApplication = sessionManager.getPendingApplication(telegramId);

        if (pendingApplication == null) {
            botMessageService.sendMessage(chatId, "🚨 Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.mainMenuKeyboard());
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

            botMessageService.sendMessage(
                    chatId,
                    "✅ Zayavkangiz qabul qilindi.\nAriza ID: " + savedApplication.getId()
                            + "\nSizga tez orada aloqaga chiqamiz. Agar kutishni istamasangiz istalgan vaqt ☎️ Aloqa bo‘limi orqali bizga bog‘lanishingiz mumkin.",
                    KeyboardUtil.mainMenuKeyboard()
            );
        } catch (Exception e) {
            e.printStackTrace();
            botMessageService.sendMessage(chatId, "❌ Zayavkani saqlashda xatolik bo‘ldi.");
        }
    }

    private void showCourses(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            botMessageService.sendMessage(chatId, "Hozircha aktiv kurslar yo‘q.❌", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        botMessageService.sendMessage(chatId, "Kurslardan birini tanlang: 👇", KeyboardUtil.coursesKeyboard(courses));
    }

    private void showCourseDetails(Long chatId, Long courseId) {
        Course course = courseService.getCourseById(courseId);

        if (course == null) {
            botMessageService.sendMessage(chatId, "Kurs topilmadi. ❌");
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
                .append(course.getCourseDuration() == null || course.getCourseDuration().isBlank()
                        ? "-"
                        : escapeHtml(course.getCourseDuration()))
                .append("\n");

        text.append("📡 <b>Format:</b> ")
                .append(formatCourseType(course.getCourseType()))
                .append("\n\n");

        if (groups.isEmpty()) {
            text.append("⚠️ Hozircha aktiv guruhlar yo‘q.");
            botMessageService.sendMessage(chatId, text.toString());
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

        text.append("\nAgar kursga qabul qilinish uchun ariza topshirmoqchi bo'lsangiz, Kerakli guruhni tanlang: 👇 ");

        botMessageService.sendMessage(chatId, text.toString(), KeyboardUtil.courseDetailsKeyboard(course, groups));
    }

    private void showPrices(Long chatId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            botMessageService.sendMessage(chatId, "❌ Hozircha aktiv kurslar topilmadi.", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        botMessageService.sendMessage(chatId, buildPricesMessage(courses), KeyboardUtil.mainMenuKeyboard());
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
        botMessageService.sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
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

        botMessageService.sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
    }

    private void sendMainMenu(Long chatId) {
        String text = """
                👋 Assalomu alaykum! Xush kelibsiz!
                
                Bizning xizmatimizdan foydalanish uchun quyidagi bo‘limlardan birini tanlang:
                """;
        botMessageService.sendMessage(chatId, text, KeyboardUtil.mainMenuKeyboard());
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
                || BTN_ADD_GROUP.equals(text)
                || "/admin".equals(text);
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

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        return phone.matches("^\\+\\d{9,15}$");
    }

    private String buildTelegramName(org.telegram.telegrambots.meta.api.objects.User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? "Telegram User" : fullName;
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
            default -> escapeHtml(courseType);
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
}