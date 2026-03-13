package uz.educenter.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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
        row2.add("🚪 Admin chiqish");
        row2.add("🏠 Bosh menu");

        keyboard.add(row1);
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        return markup;
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