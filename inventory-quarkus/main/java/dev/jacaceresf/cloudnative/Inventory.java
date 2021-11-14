package dev.jacaceresf.cloudnative;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "INVENTORY")
public class Inventory extends PanacheEntity {

    public int quantity;

    @Override
    public String toString() {
        return "Inventory{" +
                "quantity=" + quantity +
                '}';
    }
}
