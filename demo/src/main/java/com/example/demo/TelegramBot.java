package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String responseText;

            if (message.hasText()) {
                String messageText = message.getText();
                if ("/start".equals(messageText)) {
                    responseText = "Welcome!";
                } else {
                    responseText = "Message: *" + messageText + "*";
                }
            } else {
                responseText = "I understand only text";
            }
            sendNotification(chatId, responseText);
        }
    }

    void sendNotification(Long chatId, String responseText) {
        System.out.println(responseText);
        SendMessage responseMessage = new SendMessage(chatId.toString(), responseText);
        responseMessage.enableMarkdown(true);

        List<List<String>> buttons = Arrays.asList(
                Arrays.asList("Отправить рассылку"),
                Arrays.asList("Изменить текст рассылки", "Добавить человека в бд")
        );
        responseMessage.setReplyMarkup(getReplyMarkup(buttons));

        try {
            execute(responseMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private ReplyKeyboardMarkup getReplyMarkup(List<List<String>> buttons) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        buttons.forEach(rowButtons -> {
            KeyboardRow row = new KeyboardRow();
            rowButtons.forEach(row::add);
            keyboard.add(row);
        });

        markup.setKeyboard(keyboard);
        return markup;
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}