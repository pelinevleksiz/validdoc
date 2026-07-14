package com.validdoc.repository;

import com.validdoc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional; //aranan kullanıcının verıtabanında bulunup bulunmayacagını bılmıyorum o yuzden optional<user> donebilmeliyim. bu sınıf bunu yapmamı saglıyoç.

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}