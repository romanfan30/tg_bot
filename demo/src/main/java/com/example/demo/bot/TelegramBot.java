package com.example.demo.bot;

import com.example.demo.Notification.Notification;
import com.example.demo.Notification.NotificationFactory;
import com.example.demo.Notification.mail.EmailSenderService;
import com.example.demo.model.Person;
import com.example.demo.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    private static final String SKIP = "Оставить пустым";

    @Value("${bot.name}")
    private String botName;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.admin.username}")
    private String adminUsername;

    private final PersonRepository personRepository;
    private final EmailSenderService emailSenderService;

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Person> tempPersons = new ConcurrentHashMap<>();

    private String broadcastMessage = "Текст рассылки не установлен";

    public TelegramBot(PersonRepository personRepository, EmailSenderService emailSenderService) {
        this.personRepository = personRepository;
        this.emailSenderService = emailSenderService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        User user = message.getFrom();
        String username = user.getUserName();

        String responseText;

        if (!message.hasText()) {
            sendNotification(chatId, "Я понимаю только текст", isAdmin(username));
            return;
        }

        String messageText = message.getText();

        boolean isAdmin = isAdmin(username);

        switch (messageText) {
            case "/start":
                userStates.remove(chatId);
                tempPersons.remove(chatId);

                if (isAdmin) {
                    responseText = "👑 *Добро пожаловать, Администратор!*\n\n" +
                            "Используйте меню для управления ботом.";
                } else {
                    Optional<Person> existingPerson = personRepository.findByTelegramChatId(chatId);
                    if (existingPerson.isPresent()) {
                        responseText = "✅ Вы уже зарегистрированы!\n\n" +
                                "Имя: " + existingPerson.get().getName() + "\n" +
                                "Email: " + (existingPerson.get().getEmail() != null ? existingPerson.get().getEmail() : "не указан");
                    } else {
                        userStates.put(chatId, "REGISTRATION_NAME");
                        Person regPerson = new Person();
                        regPerson.setTelegramChatId(chatId);
                        if (username != null) {
                            regPerson.setTelegram("@" + username);
                        }
                        tempPersons.put(chatId, regPerson);
                        responseText = "👋 Добро пожаловать!\n\n" +
                                "📝 Для начала работы необходимо зарегистрироваться.\n\n" +
                                "Введите ваше имя:";
                    }
                }
                break;

            case "Показать список":
                if (!isAdmin) {
                    responseText = "❌ Эта функция доступна только администратору";
                } else {
                    responseText = getPersonList();
                }
                break;

            case "Добавить человека в бд":
                if (!isAdmin) {
                    responseText = "❌ Эта функция доступна только администратору";
                } else {
                    userStates.put(chatId, "WAITING_NAME");
                    Person newPerson = new Person();
                    tempPersons.put(chatId, newPerson);
                    responseText = "📝 Введите имя:";
                }
                break;

            case "Изменить текст рассылки":
                if (!isAdmin) {
                    responseText = "❌ Эта функция доступна только администратору";
                } else {
                    userStates.put(chatId, "WAITING_BROADCAST_TEXT");
                    responseText = "Введите новый текст рассылки:\n\n*Текущий текст:*\n" + broadcastMessage;
                }
                break;

            case "Отправить рассылку":
                if (!isAdmin) {
                    responseText = "❌ Эта функция доступна только администратору";
                } else {
                    responseText = sendBroadcast();
                }
                break;

            case "Показать текст рассылки":
                if (!isAdmin) {
                    responseText = "❌ Эта функция доступна только администратору";
                } else {
                    responseText = "📝 *Текущий текст рассылки:*\n\n" + broadcastMessage;
                }
                break;

            default:
                if (userStates.containsKey(chatId)) {
                    responseText = handleUserState(chatId, messageText, username);
                } else {
                    if (isAdmin) {
                        responseText = "Сообщение: *" + messageText + "*\n\nИспользуйте меню для управления ботом.";
                    } else {
                        responseText = "Используйте /start для начала работы";
                    }
                }
        }

        sendNotification(chatId, responseText, isAdmin);
    }

    private boolean isAdmin(String username) {
        return username != null && username.equalsIgnoreCase(adminUsername);
    }

    private String handleUserState(Long chatId, String messageText, String username) {
        String state = userStates.get(chatId);
        Person person = tempPersons.get(chatId);
        boolean isAdmin = isAdmin(username);

        switch (state) {
            case "REGISTRATION_NAME":
                person.setName(messageText);
                userStates.put(chatId, "REGISTRATION_EMAIL");
                return "Введите ваш email (или нажмите 'Оставить пустым'):";

            case "REGISTRATION_EMAIL":
                if (SKIP.equals(messageText)) {
                    person.setEmail(null);
                } else {
                    person.setEmail(messageText);
                }

                personRepository.save(person);
                userStates.remove(chatId);
                tempPersons.remove(chatId);
                return "✅ *Регистрация завершена!*\n\n" +
                        "Имя: " + person.getName() + "\n" +
                        "Email: " + (person.getEmail() != null ? person.getEmail() : "не указан") + "\n\n" +
                        "Теперь вы будете получать уведомления от администратора.";

            case "WAITING_NAME":
                if (!isAdmin) {
                    userStates.remove(chatId);
                    tempPersons.remove(chatId);
                    return "❌ У вас нет прав для этой операции";
                }
                person.setName(messageText);
                userStates.put(chatId, "WAITING_TELEGRAM");
                return "Введите Telegram username (или нажмите 'Оставить пустым'):";

            case "WAITING_TELEGRAM":
                if (!isAdmin) {
                    userStates.remove(chatId);
                    tempPersons.remove(chatId);
                    return "❌ У вас нет прав для этой операции";
                }
                if (SKIP.equals(messageText)) {
                    person.setTelegram(null);
                } else {
                    person.setTelegram(messageText);
                }
                userStates.put(chatId, "WAITING_CHAT_ID");
                return "Введите Telegram Chat ID (или нажмите 'Оставить пустым'):\n\n" +
                        "💡 Человек может узнать свой ID через @userinfobot";

            case "WAITING_CHAT_ID":
                if (!isAdmin) {
                    userStates.remove(chatId);
                    tempPersons.remove(chatId);
                    return "❌ У вас нет прав для этой операции";
                }
                if (SKIP.equals(messageText)) {
                    person.setTelegramChatId(null);
                } else {
                    try {
                        person.setTelegramChatId(Long.parseLong(messageText));
                    } catch (NumberFormatException e) {
                        return "❌ Ошибка: введите числовой Chat ID (или нажмите 'Оставить пустым')";
                    }
                }
                userStates.put(chatId, "WAITING_EMAIL");
                return "Введите email (или нажмите 'Оставить пустым'):";

            case "WAITING_EMAIL":
                if (!isAdmin) {
                    userStates.remove(chatId);
                    tempPersons.remove(chatId);
                    return "❌ У вас нет прав для этой операции";
                }
                if (SKIP.equals(messageText)) {
                    person.setEmail(null);
                } else {
                    person.setEmail(messageText);
                }
                personRepository.save(person);
                userStates.remove(chatId);
                tempPersons.remove(chatId);
                return "✅ *Человек успешно добавлен!*\n\n" +
                        "Имя: " + person.getName() + "\n" +
                        "Chat ID: " + (person.getTelegramChatId() != null ? person.getTelegramChatId() : "не указан") + "\n" +
                        "Email: " + (person.getEmail() != null ? person.getEmail() : "не указан");

            case "WAITING_BROADCAST_TEXT":
                if (!isAdmin) {
                    userStates.remove(chatId);
                    return "❌ У вас нет прав для этой операции";
                }
                broadcastMessage = messageText;
                userStates.remove(chatId);
                return "✅ Текст рассылки обновлен!";

            default:
                userStates.remove(chatId);
                return "Неизвестное состояние";
        }
    }

    private String getPersonList() {
        List<Person> persons = personRepository.findAll();
        if (persons.isEmpty()) return "📋 Список пуст";

        StringBuilder sb = new StringBuilder("📋 *Список зарегистрированных пользователей:*\n\n");
        int count = 1;
        for (Person p : persons) {
            sb.append(count++).append(". *").append(p.getName()).append("*\n");

            if (p.getTelegram() != null) {
                sb.append("   👤 Telegram: ").append(p.getTelegram()).append("\n");
            }

            sb.append("   🆔 Chat ID: ")
                    .append(p.getTelegramChatId() != null ? p.getTelegramChatId() : "не указан")
                    .append("\n");

            sb.append("   📧 Email: ")
                    .append(p.getEmail() != null ? p.getEmail() : "не указан")
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String sendBroadcast() {
        List<Person> persons = personRepository.findAll();

        if (persons.isEmpty()) {
            return "❌ Список пользователей пуст.";
        }

        if ("Текст рассылки не установлен".equals(broadcastMessage)) {
            return "❌ Сначала установите текст рассылки!";
        }

        int emailCount = 0;
        int telegramCount = 0;
        int errorCount = 0;

        for (Person person : persons) {
            if (person.getEmail() != null && !person.getEmail().isEmpty()) {
                try {
                    Notification email = NotificationFactory.createNotification("email", emailSenderService, null);
                    email.send(broadcastMessage, person.getEmail());
                    emailCount++;
                } catch (Exception e) {
                    System.err.println("❌ Ошибка email для " + person.getName() + ": " + e.getMessage());
                    errorCount++;
                }
            }

            if (person.getTelegramChatId() != null) {
                try {
                    Notification telegram = NotificationFactory.createNotification("telegram", null, this);
                    telegram.send(broadcastMessage, String.valueOf(person.getTelegramChatId()));
                    telegramCount++;
                } catch (Exception e) {
                    System.err.println("❌ Ошибка telegram для " + person.getName() + ": " + e.getMessage());
                    errorCount++;
                }
            }
        }

        return "✅ *Рассылка завершена!*\n\n" +
                "📧 Email отправлено: " + emailCount + "\n" +
                "📱 Telegram отправлено: " + telegramCount + "\n" +
                (errorCount > 0 ? "⚠️ Ошибок: " + errorCount : "");
    }

    void sendNotification(Long chatId, String responseText, boolean isAdmin) {
        SendMessage message = new SendMessage(chatId.toString(), responseText);
        message.enableMarkdown(true);
        message.setReplyMarkup(getReplyMarkup(getButtons(chatId, isAdmin)));
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private List<List<String>> getButtons(Long chatId, boolean isAdmin) {
        if (userStates.containsKey(chatId)) {
            String state = userStates.get(chatId);
            if (state.contains("EMAIL") || state.contains("TELEGRAM") || state.contains("CHAT_ID")) {
                return Collections.singletonList(Collections.singletonList(SKIP));
            }
        }

        if (isAdmin) {
            return Arrays.asList(
                    Arrays.asList("Показать список", "Добавить человека в бд"),
                    Arrays.asList("Показать текст рассылки"),
                    Arrays.asList("Изменить текст рассылки", "Отправить рассылку")
            );
        } else {
            return new ArrayList<>();
        }
    }

    private ReplyKeyboardMarkup getReplyMarkup(List<List<String>> buttons) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        for (List<String> rowButtons : buttons) {
            KeyboardRow row = new KeyboardRow();
            row.addAll(rowButtons);
            keyboard.add(row);
        }
        markup.setKeyboard(keyboard);
        markup.setResizeKeyboard(true);
        return markup;
    }

    @Override
    public String getBotUsername() { return botName; }

    @Override
    public String getBotToken() { return botToken; }
}
