package uz.educenter.bot.handler;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.educenter.bot.bot.BotMessageService;
import uz.educenter.bot.model.Application;
import uz.educenter.bot.model.ApplicationStatus;
import uz.educenter.bot.model.Course;
import uz.educenter.bot.model.CourseGroup;
import uz.educenter.bot.service.AdminService;
import uz.educenter.bot.service.ApplicationService;
import uz.educenter.bot.service.CourseService;
import uz.educenter.bot.service.UserService;
import uz.educenter.bot.state.PendingCourseGroup;
import uz.educenter.bot.state.SessionManager;
import uz.educenter.bot.state.UserState;
import uz.educenter.bot.util.KeyboardUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class AdminHandler {

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
    private final AdminService adminService;
    private final ApplicationService applicationService;
    private final SessionManager sessionManager;
    private final BotMessageService botMessageService;

    public AdminHandler(
            CourseService courseService,
            UserService userService,
            AdminService adminService,
            ApplicationService applicationService,
            SessionManager sessionManager,
            BotMessageService botMessageService
    ) {
        this.courseService = courseService;
        this.userService = userService;
        this.adminService = adminService;
        this.applicationService = applicationService;
        this.sessionManager = sessionManager;
        this.botMessageService = botMessageService;
    }

    public boolean handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text = message.getText().trim();
        UserState currentState = sessionManager.getUserState(telegramId);

        if ("/start".equals(text) || BTN_MAIN_MENU.equals(text)) {
            if (currentState == UserState.WAITING_ADMIN_PASSWORD || isAdminNewGroupFlowActive(telegramId)) {
                sessionManager.clearUserState(telegramId);
                sessionManager.clearPendingCourseGroup(telegramId);
            }
            return false;
        }

        if ("/cancel".equals(text) || BTN_CANCEL.equals(text)) {
            if (currentState == UserState.WAITING_ADMIN_PASSWORD) {
                sessionManager.clearUserState(telegramId);
                botMessageService.sendMessage(chatId, "Admin login bekor qilindi. ✅", KeyboardUtil.mainMenuKeyboard());
                return true;
            }

            if (isAdminNewGroupFlowActive(telegramId)) {
                sessionManager.clearUserState(telegramId);
                sessionManager.clearPendingCourseGroup(telegramId);
                botMessageService.sendMessage(chatId, "Yangi guruh yaratish jarayoni bekor qilindi. ✅", KeyboardUtil.adminMenuKeyboard());
                return true;
            }

            return false;
        }

        if (currentState == UserState.WAITING_ADMIN_PASSWORD) {
            handleAdminPassword(chatId, telegramId, text);
            return true;
        }

        if (isAdminNewGroupInputState(currentState) && isBlockedDuringAdminGroupFlow(text)) {
            botMessageService.sendMessage(
                    chatId,
                    "Siz hozir yangi guruh yaratish jarayonidasiz. Davom eting yoki ❌ Bekor qilish ni bosing.",
                    KeyboardUtil.cancelKeyboard()
            );
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_NAME) {
            handleAdminNewGroupName(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_DAYS) {
            handleAdminNewGroupDays(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_START_TIME) {
            handleAdminNewGroupStartTime(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_END_TIME) {
            handleAdminNewGroupEndTime(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_START_DATE) {
            handleAdminNewGroupStartDate(chatId, telegramId, text);
            return true;
        }

        if (currentState == UserState.WAITING_ADMIN_NEW_GROUP_END_DATE) {
            handleAdminNewGroupEndDate(chatId, telegramId, text);
            return true;
        }

        if ("/admin".equals(text) || BTN_ADMIN.equals(text)) {
            handleAdminEntry(chatId, telegramId);
            return true;
        }

        if (!sessionManager.isAdminAuthenticated(telegramId)) {
            return false;
        }

        if (BTN_NEW_APPLICATIONS.equals(text)) {
            showApplications(chatId, true);
            return true;
        }

        if (BTN_ALL_APPLICATIONS.equals(text)) {
            showApplications(chatId, false);
            return true;
        }

        if (BTN_ADD_GROUP.equals(text)) {
            startAdminAddGroupFlow(chatId, telegramId);
            return true;
        }

        if (BTN_ADMIN_LOGOUT.equals(text)) {
            sessionManager.logoutAdmin(telegramId);
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "Admin session yopildi. ✅", KeyboardUtil.mainMenuKeyboard());
            return true;
        }

        return false;
    }

    public boolean handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();

        if (data.startsWith("admin_group_save:")) {
            if (!sessionManager.isAdminAuthenticated(telegramId)) {
                botMessageService.answerCallback(callbackQuery.getId(), "🚫 Ruxsat yo‘q");
                return true;
            }

            String decision = data.split(":")[1];

            if (callbackQuery.getMessage() != null) {
                botMessageService.clearInlineKeyboard(chatId, callbackQuery.getMessage().getMessageId());
            }

            if ("no".equals(decision)) {
                sessionManager.clearPendingCourseGroup(telegramId);
                botMessageService.answerCallback(callbackQuery.getId(), "Bekor qilindi");
                botMessageService.sendMessage(chatId, "Yangi guruh yaratish bekor qilindi.", KeyboardUtil.adminMenuKeyboard());
                return true;
            }

            PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

            if (pendingCourseGroup == null) {
                botMessageService.answerCallback(callbackQuery.getId(), "Jarayon topilmadi");
                botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
                return true;
            }

            try {
                CourseGroup courseGroup = new CourseGroup();
                courseGroup.setCourseId(pendingCourseGroup.getCourseId());
                courseGroup.setGroupName(pendingCourseGroup.getGroupName());
                courseGroup.setDaysText(pendingCourseGroup.getDaysText());
                courseGroup.setStartTime(pendingCourseGroup.getStartTime());
                courseGroup.setEndTime(pendingCourseGroup.getEndTime());
                courseGroup.setStartDate(pendingCourseGroup.getStartDate());
                courseGroup.setEndDate(pendingCourseGroup.getEndDate());
                courseGroup.setIsActive(true);

                CourseGroup savedGroup = courseService.createCourseGroup(courseGroup);

                sessionManager.clearPendingCourseGroup(telegramId);

                botMessageService.answerCallback(callbackQuery.getId(), "Saqlandi ✅");
                botMessageService.sendMessage(
                        chatId,
                        "✅ Yangi guruh muvaffaqiyatli yaratildi.\nGuruh ID: " + (savedGroup != null ? savedGroup.getId() : "-"),
                        KeyboardUtil.adminMenuKeyboard()
                );
            } catch (IllegalArgumentException e) {
                botMessageService.answerCallback(callbackQuery.getId(), "Xatolik");
                botMessageService.sendMessage(chatId, "❌ " + e.getMessage(), KeyboardUtil.adminMenuKeyboard());
            } catch (Exception e) {
                e.printStackTrace();
                botMessageService.answerCallback(callbackQuery.getId(), "Xatolik");
                botMessageService.sendMessage(chatId, "❌ Yangi guruhni saqlashda xatolik bo‘ldi.", KeyboardUtil.adminMenuKeyboard());
            }

            return true;
        }

        if (data.startsWith("app_viewed:")) {
            if (!sessionManager.isAdminAuthenticated(telegramId)) {
                botMessageService.answerCallback(callbackQuery.getId(), "🚨 Ruxsat yo‘q");
                return true;
            }

            Long applicationId = Long.parseLong(data.split(":")[1]);
            boolean updated = applicationService.markAsViewed(applicationId);

            if (updated) {
                botMessageService.answerCallback(callbackQuery.getId(), "VIEWED qilindi 👌");

                if (callbackQuery.getMessage() != null) {
                    botMessageService.clearInlineKeyboard(chatId, callbackQuery.getMessage().getMessageId());
                }

                botMessageService.sendMessage(chatId, "Zayavka #" + applicationId + " VIEWED qilindi. 👌", KeyboardUtil.adminMenuKeyboard());
            } else {
                botMessageService.answerCallback(callbackQuery.getId(), "🚨 Xatolik yuz berdi");
            }

            return true;
        }

        if (data.startsWith("course:")) {
            if (!sessionManager.isAdminAuthenticated(telegramId)) {
                return false;
            }

            Long courseId = Long.parseLong(data.split(":")[1]);
            PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

            if (pendingCourseGroup == null || pendingCourseGroup.getCourseId() != null) {
                return false;
            }

            Course selectedCourse = courseService.getCourseById(courseId);

            if (selectedCourse == null) {
                botMessageService.answerCallback(callbackQuery.getId(), "❌ Kurs topilmadi");
                return true;
            }

            pendingCourseGroup.setCourseId(courseId);
            sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_NAME);

            if (callbackQuery.getMessage() != null) {
                botMessageService.clearInlineKeyboard(chatId, callbackQuery.getMessage().getMessageId());
            }

            botMessageService.answerCallback(callbackQuery.getId(), "Kurs tanlandi ✅");
            botMessageService.sendMessage(chatId, "Yangi guruh nomini kiriting. Masalan: B6", KeyboardUtil.cancelKeyboard());
            return true;
        }

        return false;
    }

    private void handleAdminEntry(Long chatId, Long telegramId) {
        if (!adminService.isAllowedAdmin(telegramId)) {
            botMessageService.sendMessage(chatId, "Siz admin sifatida ro‘yxatdan o‘tmagansiz. ❌", KeyboardUtil.mainMenuKeyboard());
            return;
        }

        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_PASSWORD);
        botMessageService.sendMessage(chatId, "Admin parolini kiriting: 🔑");
    }

    private void handleAdminPassword(Long chatId, Long telegramId, String password) {
        boolean authenticated = adminService.authenticate(telegramId, password);

        if (!authenticated) {
            botMessageService.sendMessage(chatId, "❌ Parol noto‘g‘ri. Qayta urinib ko‘ring:");
            return;
        }

        sessionManager.clearUserState(telegramId);
        sessionManager.authenticateAdmin(telegramId);
        botMessageService.sendMessage(chatId, "🤝 Admin panelga xush kelibsiz.", KeyboardUtil.adminMenuKeyboard());
    }

    private void startAdminAddGroupFlow(Long chatId, Long telegramId) {
        List<Course> courses = courseService.getAllActiveCourses();

        if (courses.isEmpty()) {
            botMessageService.sendMessage(chatId, "❌ Aktiv kurslar topilmadi.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        sessionManager.clearUserState(telegramId);
        sessionManager.clearPendingCourseGroup(telegramId);
        sessionManager.createPendingCourseGroup(telegramId);

        botMessageService.sendMessage(
                chatId,
                "Yangi guruh qaysi kurs uchun yaratiladi? Kursni tanlang 👇",
                KeyboardUtil.coursesKeyboard(courses)
        );
    }

    private void handleAdminNewGroupName(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null || pendingCourseGroup.getCourseId() == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        if (text.isBlank()) {
            botMessageService.sendMessage(chatId, "❌ Guruh nomi bo‘sh bo‘lmasligi kerak. Qayta kiriting:", KeyboardUtil.cancelKeyboard());
            return;
        }

        pendingCourseGroup.setGroupName(text.trim());
        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_DAYS);

        botMessageService.sendMessage(
                chatId,
                "Dars kunlarini kiriting.\nMasalan: Dushanba, Chorshanba, Juma",
                KeyboardUtil.cancelKeyboard()
        );
    }

    private void handleAdminNewGroupDays(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        if (text.isBlank()) {
            botMessageService.sendMessage(chatId, "❌ Dars kunlari bo‘sh bo‘lmasligi kerak. Qayta kiriting:", KeyboardUtil.cancelKeyboard());
            return;
        }

        pendingCourseGroup.setDaysText(text.trim());
        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_START_TIME);

        botMessageService.sendMessage(
                chatId,
                "Boshlanish vaqtini kiriting.\nFormat: HH:mm\nMasalan: 19:00",
                KeyboardUtil.cancelKeyboard()
        );
    }

    private void handleAdminNewGroupStartTime(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        LocalTime startTime = parseHourMinute(text);

        if (startTime == null) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Vaqt formati noto‘g‘ri.\nFormat: HH:mm\nMasalan: 19:00",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        pendingCourseGroup.setStartTime(startTime);
        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_END_TIME);

        botMessageService.sendMessage(
                chatId,
                "Tugash vaqtini kiriting.\nFormat: HH:mm\nMasalan: 20:30",
                KeyboardUtil.cancelKeyboard()
        );
    }

    private void handleAdminNewGroupEndTime(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null || pendingCourseGroup.getStartTime() == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        LocalTime endTime = parseHourMinute(text);

        if (endTime == null) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Vaqt formati noto‘g‘ri.\nFormat: HH:mm\nMasalan: 20:30",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        if (!endTime.isAfter(pendingCourseGroup.getStartTime())) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Tugash vaqti boshlanish vaqtidan keyin bo‘lishi kerak. Qayta kiriting:",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        pendingCourseGroup.setEndTime(endTime);
        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_START_DATE);

        botMessageService.sendMessage(
                chatId,
                "Boshlanish sanasini kiriting.\nFormat: yyyy-MM-dd\nMasalan: 2026-03-20",
                KeyboardUtil.cancelKeyboard()
        );
    }

    private void handleAdminNewGroupStartDate(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        LocalDate startDate = parseIsoDate(text);

        if (startDate == null) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Sana formati noto‘g‘ri.\nFormat: yyyy-MM-dd\nMasalan: 2026-03-20",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        pendingCourseGroup.setStartDate(startDate);
        sessionManager.setUserState(telegramId, UserState.WAITING_ADMIN_NEW_GROUP_END_DATE);

        botMessageService.sendMessage(
                chatId,
                "Tugash sanasini kiriting.\nFormat: yyyy-MM-dd\nMasalan: 2026-06-20",
                KeyboardUtil.cancelKeyboard()
        );
    }

    private void handleAdminNewGroupEndDate(Long chatId, Long telegramId, String text) {
        PendingCourseGroup pendingCourseGroup = sessionManager.getPendingCourseGroup(telegramId);

        if (pendingCourseGroup == null || pendingCourseGroup.getStartDate() == null) {
            sessionManager.clearUserState(telegramId);
            sessionManager.clearPendingCourseGroup(telegramId);
            botMessageService.sendMessage(chatId, "❌ Jarayon uzilib qoldi. Qaytadan boshlang.", KeyboardUtil.adminMenuKeyboard());
            return;
        }

        LocalDate endDate = parseIsoDate(text);

        if (endDate == null) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Sana formati noto‘g‘ri.\nFormat: yyyy-MM-dd\nMasalan: 2026-06-20",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        if (endDate.isBefore(pendingCourseGroup.getStartDate())) {
            botMessageService.sendMessage(
                    chatId,
                    "❌ Tugash sanasi boshlanish sanasidan oldin bo‘lishi mumkin emas. Qayta kiriting:",
                    KeyboardUtil.cancelKeyboard()
            );
            return;
        }

        pendingCourseGroup.setEndDate(endDate);
        sessionManager.clearUserState(telegramId);

        botMessageService.sendMessage(
                chatId,
                buildPendingCourseGroupPreview(pendingCourseGroup),
                KeyboardUtil.adminGroupConfirmKeyboard()
        );
    }

    private void showApplications(Long chatId, boolean onlyNew) {
        List<Application> applications = onlyNew
                ? applicationService.getApplicationsByStatus(ApplicationStatus.NEW)
                : applicationService.getAllApplications();

        if (applications.isEmpty()) {
            String emptyMessage = onlyNew
                    ? "❌ Yangi zayavkalar topilmadi."
                    : "❌ Zayavkalar topilmadi.";

            botMessageService.sendMessage(chatId, emptyMessage, KeyboardUtil.adminMenuKeyboard());
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
                botMessageService.sendMessage(chatId, text.toString(), KeyboardUtil.applicationActionsKeyboard(application.getId()));
            } else {
                botMessageService.sendMessage(chatId, text.toString());
            }
        }
    }

    private boolean isAdminNewGroupFlowActive(Long telegramId) {
        UserState state = sessionManager.getUserState(telegramId);
        return state == UserState.WAITING_ADMIN_NEW_GROUP_NAME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_DAYS
                || state == UserState.WAITING_ADMIN_NEW_GROUP_START_TIME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_END_TIME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_START_DATE
                || state == UserState.WAITING_ADMIN_NEW_GROUP_END_DATE
                || sessionManager.getPendingCourseGroup(telegramId) != null;
    }

    private boolean isAdminNewGroupInputState(UserState state) {
        return state == UserState.WAITING_ADMIN_NEW_GROUP_NAME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_DAYS
                || state == UserState.WAITING_ADMIN_NEW_GROUP_START_TIME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_END_TIME
                || state == UserState.WAITING_ADMIN_NEW_GROUP_START_DATE
                || state == UserState.WAITING_ADMIN_NEW_GROUP_END_DATE;
    }

    private boolean isBlockedDuringAdminGroupFlow(String text) {
        return BTN_COURSES.equals(text)
                || BTN_PRICES.equals(text)
                || BTN_LOCATION.equals(text)
                || BTN_CONTACT.equals(text)
                || BTN_APPLY.equals(text)
                || BTN_ADMIN.equals(text)
                || BTN_NEW_APPLICATIONS.equals(text)
                || BTN_ALL_APPLICATIONS.equals(text)
                || BTN_ADMIN_LOGOUT.equals(text)
                || BTN_ADD_GROUP.equals(text)
                || "/admin".equals(text);
    }

    private LocalTime parseHourMinute(String text) {
        try {
            return LocalTime.parse(text.trim() + ":00");
        } catch (Exception e) {
            try {
                return LocalTime.parse(text.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private LocalDate parseIsoDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPendingCourseGroupPreview(PendingCourseGroup pendingCourseGroup) {
        Course course = courseService.getCourseById(pendingCourseGroup.getCourseId());
        String courseName = course != null
                ? escapeHtml(course.getName())
                : String.valueOf(pendingCourseGroup.getCourseId());

        StringBuilder text = new StringBuilder();
        text.append("✅ Yangi guruh ma’lumotlari qabul qilindi:\n\n");
        text.append("📚 Kurs: ").append(courseName).append("\n");
        text.append("👥 Guruh: ").append(escapeHtml(pendingCourseGroup.getGroupName())).append("\n");
        text.append("🗓 Kunlar: ").append(escapeHtml(pendingCourseGroup.getDaysText())).append("\n");
        text.append("🕒 Vaqt: ").append(pendingCourseGroup.getStartTime()).append(" - ").append(pendingCourseGroup.getEndTime()).append("\n");
        text.append("📅 Muddat: ").append(pendingCourseGroup.getStartDate()).append(" - ").append(pendingCourseGroup.getEndDate()).append("\n\n");
        text.append("Saqlash uchun tugmani bosing.");

        return text.toString();
    }

    private String formatTelegramUsername(uz.educenter.bot.model.User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "-";
        }

        return "@" + escapeHtml(user.getUsername());
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