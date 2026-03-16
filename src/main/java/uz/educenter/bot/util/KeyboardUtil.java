package uz.educenter.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.educenter.bot.model.Course;
import uz.educenter.bot.model.CourseGroup;

import java.util.ArrayList;
import java.util.List;

public class KeyboardUtil {

    private KeyboardUtil() {
    }

    public static ReplyKeyboardMarkup mainMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📚 Kurslar");
        row1.add("💰 Narxlar");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📍 Manzil");
        row2.add("☎️ Aloqa");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("📝 Zayavka qoldirish");
        row3.add("🔐 Admin");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static ReplyKeyboardMarkup adminMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🆕 Yangi zayavkalar");
        row1.add("📋 Barcha zayavkalar");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("➕ Yangi guruh qo‘shish");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("🏠 Bosh menu");
        row3.add("🚪 Admin chiqish");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static ReplyKeyboard phoneRequestKeyboard() {
        KeyboardButton contactButton = new KeyboardButton("📱 Raqamni yuborish");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(row);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        keyboardMarkup.setKeyboard(rows);

        return keyboardMarkup;
    }

    public static ReplyKeyboard removeKeyboard() {
        ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove();
        removeKeyboard.setRemoveKeyboard(true);
        return removeKeyboard;
    }

    public static ReplyKeyboardMarkup cancelKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("❌ Bekor qilish");

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        return markup;
    }

    public static ReplyKeyboardMarkup phoneRequestKeyboardWithCancel() {
        KeyboardButton contactButton = new KeyboardButton("📱 Raqamni yuborish");
        contactButton.setRequestContact(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(contactButton);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("❌ Bekor qilish");

        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(row1);
        rows.add(row2);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        keyboardMarkup.setKeyboard(rows);

        return keyboardMarkup;
    }

    public static InlineKeyboardMarkup coursesKeyboard(List<Course> courses) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Course course : courses) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(course.getName());
            button.setCallbackData("course:" + course.getId());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup courseDetailsKeyboard(Course course, List<CourseGroup> groups) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (CourseGroup group : groups) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(group.getGroupName() + " | " + group.getDaysText());
            button.setCallbackData("group:" + course.getId() + ":" + group.getId());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        if (course.getDetailsUrl() != null && !course.getDetailsUrl().isBlank()) {
            InlineKeyboardButton detailsButton = new InlineKeyboardButton();
            detailsButton.setText("📖 Batafsil ma'lumot");
            detailsButton.setUrl(course.getDetailsUrl());

            List<InlineKeyboardButton> detailsRow = new ArrayList<>();
            detailsRow.add(detailsButton);
            rows.add(detailsRow);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup courseGroupsKeyboard(Long courseId, List<CourseGroup> groups) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (CourseGroup group : groups) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(group.getGroupName() + " | " + group.getDaysText());
            button.setCallbackData("group:" + courseId + ":" + group.getId());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup applicationConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("✅ Ha");
        yesButton.setCallbackData("apply_confirm:yes");

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText("❌ Yo‘q");
        noButton.setCallbackData("apply_confirm:no");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(yesButton);
        row.add(noButton);

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup adminGroupConfirmKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton saveButton = new InlineKeyboardButton();
        saveButton.setText("✅ Saqlash");
        saveButton.setCallbackData("admin_group_save:yes");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Bekor qilish");
        cancelButton.setCallbackData("admin_group_save:no");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(saveButton);
        row.add(cancelButton);

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup applicationActionsKeyboard(Long applicationId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton viewedButton = new InlineKeyboardButton();
        viewedButton.setText("✅ VIEWED qilish");
        viewedButton.setCallbackData("app_viewed:" + applicationId);

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(viewedButton);
        rows.add(row);

        markup.setKeyboard(rows);
        return markup;
    }
}