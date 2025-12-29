package com.example.demo.Notification;

import com.example.demo.Notification.mail.EmailSenderService;
import com.example.demo.bot.TelegramBot;

public class NotificationFactory {

   public static Notification createNotification(String type, EmailSenderService emailSenderService, TelegramBot telegramBot) {
        switch (type.toLowerCase()) {
            case "email":
                return new NotificationMail(emailSenderService);
            case "telegram":
                return new NotificationTelegram(telegramBot);
            default:
                throw new IllegalArgumentException("Неизвестный тип: " + type);
        }
    }
}