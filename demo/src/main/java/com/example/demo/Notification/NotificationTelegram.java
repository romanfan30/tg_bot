package com.example.demo.Notification;

import com.example.demo.bot.TelegramBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class NotificationTelegram implements Notification {

    private final TelegramBot telegramBot;

    public NotificationTelegram(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void send(String message, String recipient) {
        if (telegramBot == null) {
            System.err.println("TelegramBot не передан!");
            return;
        }

        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(recipient);
            sendMessage.setText(message);
            sendMessage.enableMarkdown(true);

            telegramBot.execute(sendMessage);

            System.out.println("Telegram сообщение отправлено на chat_id: " + recipient);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки Telegram сообщения: " + e.getMessage());
        }
    }
}