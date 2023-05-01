package app.cash.backfila.client.sqldelight.persistence

import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import java.time.LocalDate
import javax.inject.Inject

class TestHockeyData @Inject constructor(
  val hockeyDataDb: HockeyDataDatabase,
) {
  fun insertAnaheimDucks() {
    val hockeyPlayerQueries = hockeyDataDb.hockeyPlayerQueries
    hockeyPlayerQueries.insertPlayer(39, "Sam Carrick", PlayerPosition.C, PlayerShoots.R, "6' 0", 200, LocalDate.parse("1992-02-04"), "Markham, ON, CAN")
    hockeyPlayerQueries.insertPlayer(44, "Max Comtois", PlayerPosition.LW, PlayerShoots.L, "6' 2", 210, LocalDate.parse("1999-01-08"), "Longueuil, QC, CAN")
    hockeyPlayerQueries.insertPlayer(38, "Derek Grant", PlayerPosition.C, PlayerShoots.L, "6' 3", 210, LocalDate.parse("1990-04-20"), "Abbotsford, BC, CAN")
    hockeyPlayerQueries.insertPlayer(14, "Adam Henrique (A)", PlayerPosition.C, PlayerShoots.L, "6' 0", 195, LocalDate.parse("1990-02-06"), "Brantford, ON, CAN")
    hockeyPlayerQueries.insertPlayer(49, "Max Jones", PlayerPosition.LW, PlayerShoots.L, "6' 3", 216, LocalDate.parse("1998-02-17"), "Rochester, MI, USA")
    hockeyPlayerQueries.insertPlayer(20, "Brett Leason", PlayerPosition.RW, PlayerShoots.R, "6' 5", 218, LocalDate.parse("1999-04-30"), "Calgary, AB, CAN")
    hockeyPlayerQueries.insertPlayer(21, "Isac Lundestrom", PlayerPosition.C, PlayerShoots.L, "6' 0", 193, LocalDate.parse("1999-11-06"), "Gallivare, SWE")
    hockeyPlayerQueries.insertPlayer(26, "Brock McGinn", PlayerPosition.LW, PlayerShoots.L, "6' 0", 187, LocalDate.parse("1994-02-02"), "Fergus, ON, CAN")
    hockeyPlayerQueries.insertPlayer(37, "Mason McTavish", PlayerPosition.C, PlayerShoots.L, "6' 0", 213, LocalDate.parse("2003-01-30"), "Zurich, CHE")
    hockeyPlayerQueries.insertPlayer(7, "Jayson Megna", PlayerPosition.C, PlayerShoots.R, "6' 1", 195, LocalDate.parse("1990-02-01"), "Fort Lauderdale, FL, USA")
    hockeyPlayerQueries.insertPlayer(62, "Nikita Nesterenko", PlayerPosition.C, PlayerShoots.L, "6' 2", 183, LocalDate.parse("2001-09-10"), "Brooklyn, NY, USA")
    hockeyPlayerQueries.insertPlayer(33, "Jakob Silfverberg (A)", PlayerPosition.RW, PlayerShoots.R, "6' 1", 207, LocalDate.parse("1990-10-13"), "Gävle, SWE")
    hockeyPlayerQueries.insertPlayer(16, "Ryan Strome", PlayerPosition.C, PlayerShoots.R, "6' 1", 191, LocalDate.parse("1993-07-11"), "Mississauga, ON, CAN")
    hockeyPlayerQueries.insertPlayer(19, "Troy Terry", PlayerPosition.RW, PlayerShoots.R, "6' 0", 185, LocalDate.parse("1997-09-10"), "Denver, CO, USA")
    hockeyPlayerQueries.insertPlayer(77, "Frank Vatrano", PlayerPosition.RW, PlayerShoots.L, "5' 1", 197, LocalDate.parse("1994-03-14"), "East Longmeadow, MA, USA")
    hockeyPlayerQueries.insertPlayer(11, "Trevor Zegras", PlayerPosition.C, PlayerShoots.L, "6' 0", 185, LocalDate.parse("2001-03-20"), "Bedford, NY, USA")
  }
}
