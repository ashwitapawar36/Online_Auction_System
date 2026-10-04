import java.sql.*;

public class UserService {

    // =====================================================
    // REGISTER USER
    // =====================================================

    public int registerUser(
            String name,
            String email
    ) throws Exception {

        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                String sql =
                        "INSERT INTO public.users " +
                        "(name, email) " +
                        "VALUES (?, ?) " +
                        "RETURNING user_id";

                int userId;

                try (PreparedStatement ps =
                             con.prepareStatement(sql)) {

                    ps.setString(1, name);
                    ps.setString(2, email);

                    ResultSet rs =
                            ps.executeQuery();

                    rs.next();

                    userId =
                            rs.getInt("user_id");
                }

                int logId =
                        ReplicationService.createLog(
                                con,
                                "users",
                                "INSERT",
                                userId,
                                null,
                                "name=" + name +
                                        ",email=" + email
                        );

                con.commit();

                ReplicationService.replicateUser(
                        logId,
                        "INSERT",
                        userId,
                        name,
                        email
                );

                return userId;

            } catch (Exception e) {

                con.rollback();

                throw e;
            }
        }
    }


    // =====================================================
    // UPDATE USER
    // =====================================================

    public void updateUser(
            int userId,
            String name,
            String email
    ) throws Exception {

        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                String oldName;
                String oldEmail;

                String select =
                        "SELECT name, email " +
                        "FROM public.users " +
                        "WHERE user_id = ? " +
                        "FOR UPDATE";

                try (PreparedStatement ps =
                             con.prepareStatement(select)) {

                    ps.setInt(1, userId);

                    ResultSet rs =
                            ps.executeQuery();

                    if (!rs.next()) {

                        throw new Exception(
                                "User not found."
                        );
                    }

                    oldName =
                            rs.getString("name");

                    oldEmail =
                            rs.getString("email");
                }


                String update =
                        "UPDATE public.users " +
                        "SET name = ?, email = ? " +
                        "WHERE user_id = ?";

                try (PreparedStatement ps =
                             con.prepareStatement(update)) {

                    ps.setString(1, name);
                    ps.setString(2, email);
                    ps.setInt(3, userId);

                    ps.executeUpdate();
                }


                int logId =
                        ReplicationService.createLog(
                                con,
                                "users",
                                "UPDATE",
                                userId,
                                "name=" + oldName +
                                        ",email=" + oldEmail,
                                "name=" + name +
                                        ",email=" + email
                        );

                con.commit();


                ReplicationService.replicateUser(
                        logId,
                        "UPDATE",
                        userId,
                        name,
                        email
                );

            } catch (Exception e) {

                con.rollback();

                throw e;
            }
        }
    }


    // =====================================================
    // DELETE USER
    // =====================================================

    public void deleteUser(int userId)
            throws Exception {

        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                String name;
                String email;

                String select =
                        "SELECT name, email " +
                        "FROM public.users " +
                        "WHERE user_id = ? " +
                        "FOR UPDATE";

                try (PreparedStatement ps =
                             con.prepareStatement(select)) {

                    ps.setInt(1, userId);

                    ResultSet rs =
                            ps.executeQuery();

                    if (!rs.next()) {

                        throw new Exception(
                                "User not found."
                        );
                    }

                    name =
                            rs.getString("name");

                    email =
                            rs.getString("email");
                }


                int logId =
                        ReplicationService.createLog(
                                con,
                                "users",
                                "DELETE",
                                userId,
                                "name=" + name +
                                        ",email=" + email,
                                null
                        );


                try (PreparedStatement ps =
                             con.prepareStatement(
                                     "DELETE FROM public.users " +
                                     "WHERE user_id = ?"
                             )) {

                    ps.setInt(1, userId);

                    ps.executeUpdate();
                }


                con.commit();


                ReplicationService.replicateUser(
                        logId,
                        "DELETE",
                        userId,
                        null,
                        null
                );

            } catch (Exception e) {

                con.rollback();

                throw e;
            }
        }
    }
}