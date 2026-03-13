package uz.educenter.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.educenter.bot.bot.EducationCenterBot;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new EducationCenterBot());
            System.out.println("EducationCenterBot started successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}