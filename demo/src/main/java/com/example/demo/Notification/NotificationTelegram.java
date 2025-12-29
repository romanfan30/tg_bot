package com.example.demo.Notification;

public class NotificationTelegram implements Notification {
    @Override
    public void send(String message, String recipient){
        System.out.println("Отправка Telegram на: " + recipient);
        System.out.println("Сообщение: " + message);
    }
}
