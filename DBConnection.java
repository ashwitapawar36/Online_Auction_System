import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:postgresql://localhost:5433/auction_system";

    private static final String USER = "postgres";

    private static final String PASSWORD = "ashwita";

    public static Connection getConnection()
            throws SQLException {

        return DriverManager.getConnection(
                URL,
                USER,
                PASSWORD
        );
    }

    public static void main(String[] args) {

        try (Connection con = getConnection()) {

            System.out.println(
                    "PostgreSQL connected successfully!"
            );

        } catch (Exception e) {

            System.out.println(
                    "Database connection failed."
            );

            e.printStackTrace();
        }
    }
}