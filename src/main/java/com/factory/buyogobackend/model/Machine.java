package com.factory.buyogobackend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "machines")
@Getter
@Setter
@NoArgsConstructor
public class Machine {
    @Id
    private String machineId ;
    private String factoryId ;
    private String lineId ;
}
