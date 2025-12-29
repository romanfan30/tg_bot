package com.example.demo.Notification;

import com.example.demo.DemoApplication;
import com.example.demo.Notification.mail.EmailSenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class NotificationMail implements Notification{
    private final EmailSenderService emailSenderService;

    public NotificationMail(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @Override
    public void send(String message, String recipient) {
        System.out.println("Отправка Email на: " + recipient);
        System.out.println("Сообщение: " + message);

        emailSenderService.sendEmail(
                recipient,
                "Уведомление",
                message
        );
    }
}
