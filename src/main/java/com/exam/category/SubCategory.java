package com.exam.category;

import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
@Builder
@Entity
@Table(name="sub_category")
public class SubCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long sub_category_id;

	@ManyToOne
	@JoinColumn(name="category_id")
    Category category;

	String sub_name;

	@Column(name = "expiration_period")
	Integer expirationPeriod;


}
