package com.example.demo.Notification;

public class NotificationFactory {

    public static Notification createNotification(String type) {
        switch (type.toLowerCase()) {
            case "email":
                return new NotificationMail();
            case "telegram":
                return new NotificationTelegram();
            default:
                throw new IllegalArgumentException("Неизвестный тип: " + type);
        }
    }
}