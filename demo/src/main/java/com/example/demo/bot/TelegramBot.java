package com.example.demo.bot;

import com.example.demo.model.Person;
import com.example.demo.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;

    private final PersonRepository personRepository;

    // Хранение состояний пользователей
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Person> tempPersons = new ConcurrentHashMap<>();

    public TelegramBot(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String responseText;

            if (message.hasText()) {
                String messageText = message.getText();

                if ("/start".equals(messageText)) {
                    userStates.remove(chatId);
                    responseText = "Welcome!";
                } else if ("Показать список".equals(messageText)) {
                    responseText = getPersonList();
                } else if ("Добавить человека в бд".equals(messageText)) {
                    userStates.put(chatId, "WAITING_NAME");
                    tempPersons.put(chatId, new Person());
                    responseText = "Введите имя:";
                } else if (userStates.containsKey(chatId)) {
                    responseText = handleUserState(chatId, messageText);
                } else {
                    responseText = "Message: *" + messageText + "*";
                }
            } else {
                responseText = "I understand only text";
            }
            sendNotification(chatId, responseText);
        }
    }

    private String handleUserState(Long chatId, String messageText) {
        String state = userStates.get(chatId);
        Person person = tempPersons.get(chatId);

        switch (state) {
            case "WAITING_NAME":
                person.setName(messageText);
                userStates.put(chatId, "WAITING_TELEGRAM");
                return "Введите Telegram username:";

            case "WAITING_TELEGRAM":
                person.setTelegram(messageText);
                userStates.put(chatId, "WAITING_EMAIL");
                return "Введите email:";

            case "WAITING_EMAIL":
                person.setEmail(messageText);
                personRepository.save(person);
                userStates.remove(chatId);
                tempPersons.remove(chatId);
                return "Человек успешно добавлен в базу данных!";

            default:
                userStates.remove(chatId);
                return "Неизвестное состояние";
        }
    }

    private String getPersonList() {
        List<Person> persons = personRepository.findAll();

        if (persons.isEmpty()) {
            return "Список пуст";
        }

        StringBuilder sb = new StringBuilder("📋 *Список людей:*\n\n");
        for (int i = 0; i < persons.size(); i++) {
            Person p = persons.get(i);
            sb.append(i + 1).append(". ")
                    .append("*").append(p.getName()).append("*\n")
                    .append("   Telegram: ").append(p.getTelegram()).append("\n")
                    .append("   Email: ").append(p.getEmail()).append("\n\n");
        }

        return sb.toString();
    }

    void sendNotification(Long chatId, String responseText) {
        System.out.println(responseText);
        SendMessage responseMessage = new SendMessage(chatId.toString(), responseText);
        responseMessage.enableMarkdown(true);

        List<List<String>> buttons = Arrays.asList(
                Arrays.asList("Отправить рассылку"),
                Arrays.asList("Показать список", "Добавить человека в бд")
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