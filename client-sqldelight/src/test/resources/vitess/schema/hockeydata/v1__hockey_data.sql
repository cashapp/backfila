CREATE TABLE hockeyPlayer (
  player_number INT PRIMARY KEY NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  position VARCHAR(50) NOT NULL,
  shoots VARCHAR(10) NOT NULL,
  height VARCHAR(20) NOT NULL,
  weight INT NOT NULL,
  date_of_birth DATE NOT NULL,
  place_of_birth VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
