package com.twcodeart.tcgnews.product;

import com.twcodeart.tcgnews.news.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByGame(Game game);
}
