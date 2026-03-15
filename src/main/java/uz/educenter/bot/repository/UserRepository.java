package uz.educenter.bot.repository;

import uz.educenter.bot.config.DatabaseConfig;
import uz.educenter.bot.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class UserRepository {

    public User findByTelegramId(Long telegramId) {
        String sql = """
                SELECT id, telegram_id, full_name, username, phone, created_at
                FROM users
                WHERE telegram_id = ?
                """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setLong(1, telegramId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public User findById(Long id) {
        String sql = """
            SELECT id, telegram_id, full_name, username, phone, created_at
            FROM users
            WHERE id = ?
            """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setLong(1, id);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public User save(User user) {
        String sql = """
                INSERT INTO users (telegram_id, full_name, username, phone)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setLong(1, user.getTelegramId());
            preparedStatement.setString(2, user.getFullName());
            preparedStatement.setString(3, user.getUsername());
            preparedStatement.setString(4, user.getPhone());

            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setId(generatedKeys.getLong(1));
                    }
                }
                return findByTelegramId(user.getTelegramId());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean updatePhone(Long userId, String phone) {
        String sql = """
                UPDATE users
                SET phone = ?
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setString(1, phone);
            preparedStatement.setLong(2, userId);

            return preparedStatement.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private User mapRow(ResultSet resultSet) throws Exception {
        User user = new User();
        user.setId(resultSet.getLong("id"));
        user.setTelegramId(resultSet.getLong("telegram_id"));
        user.setFullName(resultSet.getString("full_name"));
        user.setUsername(resultSet.getString("username"));
        user.setPhone(resultSet.getString("phone"));
        user.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        return user;
    }
}