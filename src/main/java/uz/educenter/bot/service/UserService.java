package uz.educenter.bot.service;

import uz.educenter.bot.model.User;
import uz.educenter.bot.repository.UserRepository;

public class UserService {

    private final UserRepository userRepository;

    public UserService() {
        this.userRepository = new UserRepository();
    }

    public User getOrCreateUser(Long telegramId, String fullName, String username) {
        User user = userRepository.findByTelegramId(telegramId);

        if (user != null) {
            return user;
        }

        User newUser = new User();
        newUser.setTelegramId(telegramId);
        newUser.setFullName(fullName);
        newUser.setUsername(username);
        newUser.setPhone(null);

        return userRepository.save(newUser);
    }

    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }
    public User findById(Long id) {
        return userRepository.findById(id);
    }

    public boolean updatePhone(Long userId, String phone) {
        return userRepository.updatePhone(userId, phone);
    }
}