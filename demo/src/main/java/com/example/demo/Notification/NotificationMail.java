package com.example.demo.Notification;

public class NotificationMail implements Notification{
    @Override
    public void send(String message, String recipient){
        System.out.println("Отправка Email на: " + recipient);
        System.out.println("Сообщение: " + message);
    }
}
