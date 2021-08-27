package com.game.controller;

import com.game.entity.*;
import com.game.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.List;

@RestController
@RequestMapping("/rest/players")
public class PlayerRestController {

    @Autowired
    private PlayerService playerService;

    @RequestMapping(value = "", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Player>> getPlayerList(@RequestParam(required = false) String name,
                                                      @RequestParam(required = false) String title,
                                                      @RequestParam(required = false) Race race,
                                                      @RequestParam(required = false) Profession profession,
                                                      @RequestParam(required = false) Long after,
                                                      @RequestParam(required = false) Long before,
                                                      @RequestParam(required = false) Boolean banned,
                                                      @RequestParam(required = false) Integer minExperience,
                                                      @RequestParam(required = false) Integer maxExperience,
                                                      @RequestParam(required = false) Integer minLevel,
                                                      @RequestParam(required = false) Integer maxLevel,
                                                      @RequestParam(required = false) PlayerOrder order,
                                                      @RequestParam(required = false) Integer pageNumber,
                                                      @RequestParam(required = false) Integer pageSize) {

        Specification<Player> spec = makeSpecification(name, title, race, profession, after, before, banned,
                minExperience, maxExperience, minLevel, maxLevel);

        if (pageNumber == null) pageNumber = 0;
        if (pageSize == null) pageSize = 3;
        if (order == null) order = PlayerOrder.ID;

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(order.name().toLowerCase()));

        List<Player> players = this.playerService.getAll(spec, pageable);

        return new ResponseEntity<>(players, HttpStatus.OK);
    }

    @RequestMapping(value = "/count", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Integer> getPlayerCount(@RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String title,
                                                  @RequestParam(required = false) Race race,
                                                  @RequestParam(required = false) Profession profession,
                                                  @RequestParam(required = false) Long after,
                                                  @RequestParam(required = false) Long before,
                                                  @RequestParam(required = false) Boolean banned,
                                                  @RequestParam(required = false) Integer minExperience,
                                                  @RequestParam(required = false) Integer maxExperience,
                                                  @RequestParam(required = false) Integer minLevel,
                                                  @RequestParam(required = false) Integer maxLevel) {

        Specification<Player> spec = makeSpecification(name, title, race, profession, after, before, banned,
                minExperience, maxExperience, minLevel, maxLevel);
        Integer playerCount = this.playerService.getCount(spec);
        return new ResponseEntity<>(playerCount, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> getPlayer(@PathVariable("id") Long playerId) {
        if (!isIdValid(playerId)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Player player = this.playerService.getById(playerId);

        if (player == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(player, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> deletePlayer(@PathVariable("id") Long playerId) {
        if (!isIdValid(playerId)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Player player = this.playerService.getById(playerId);

        if (player == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        this.playerService.delete(playerId);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> createPlayer(@RequestBody(required = false) Player player) {
        if (player == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        String name = player.getName();
        String title = player.getTitle();
        Race race = player.getRace();
        Profession profession = player.getProfession();
        Date birthday = player.getBirthday();
        // .banned is optional
        Integer experience = player.getExperience();

        // Properties validation
        if (!isNameValid(name) || !isTitleValid(title) || race == null || profession == null || !isDateValid(birthday)
                || !isExperienceValid(experience)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // .banned default value
        if (player.getBanned() == null) player.setBanned(false);

        // Calculated fields (aka The Business Logic)
        Integer level = calculateLevel(experience);
        player.setLevel(level);
        player.setUntilNextLevel(calculateUntilNextLevel(experience, level));

        this.playerService.save(player);

        return new ResponseEntity<>(player, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> updatePlayer(@PathVariable("id") Long playerId,
                                               @RequestBody(required = false) Player updatedPlayer) {

        if (!isIdValid(playerId)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Player player = this.playerService.getById(playerId);

        if (player == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (updatedPlayer == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        String name = updatedPlayer.getName();
        String title = updatedPlayer.getTitle();
        Race race = updatedPlayer.getRace();
        Profession profession = updatedPlayer.getProfession();
        Date birthday = updatedPlayer.getBirthday();
        Boolean banned = updatedPlayer.getBanned();
        Integer experience = updatedPlayer.getExperience();

        // Are all properties valid or null?
        if ((name != null && !isNameValid(name)) ||  (title != null && !isTitleValid(title))
            || (birthday != null && !isDateValid(birthday)) || (experience != null && !isExperienceValid(experience))) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Set original entity's parameters from non-null updatedPlayer fields
        if (name != null) player.setName(name);
        if (title != null) player.setTitle(title);
        if (race != null) player.setRace(race);
        if (profession != null) player.setProfession(profession);
        if (birthday != null) player.setBirthday(birthday);
        if (banned != null) player.setBanned(banned);
        if (experience != null) {
            player.setExperience(experience);
            // Also, some Business Logic, since .experience changed
            Integer level = calculateLevel(experience);
            player.setLevel(level);
            player.setUntilNextLevel(calculateUntilNextLevel(experience, level));
        }

        this.playerService.save(player);

        return new ResponseEntity<>(player, HttpStatus.OK);
    }

    private Specification<Player> makeSpecification(String name,
                                                    String title,
                                                    Race race,
                                                    Profession profession,
                                                    Long after,
                                                    Long before,
                                                    Boolean banned,
                                                    Integer minExperience,
                                                    Integer maxExperience,
                                                    Integer minLevel,
                                                    Integer maxLevel) {

        // Initialize with always True spec, then add AND clauses
        Specification<Player> resultSpecification = (playerRoot, query, builder) ->
                builder.isTrue(builder.literal(true));
        if (name != null) {
            Specification<Player> filterName = (playerRoot, query, builder) ->
                    builder.like(playerRoot.get("name"), "%" + name + "%");
            resultSpecification = resultSpecification.and(filterName);
        }
        if (title != null) {
            Specification<Player> filterTitle = (playerRoot, query, builder) ->
                    builder.like(playerRoot.get("title"), "%" + title + "%");
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterTitle) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (race != null) {
            Specification<Player> filterRace = (playerRoot, query, builder) ->
                    builder.equal(playerRoot.get("race"), race);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterRace) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (profession != null) {
            Specification<Player> filterProfession = (playerRoot, query, builder) ->
                    builder.equal(playerRoot.get("profession"), profession);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterProfession) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (after != null) {
            Specification<Player> filterAfter = (playerRoot, query, builder) ->
                    builder.greaterThanOrEqualTo(playerRoot.get("birthday"), new Date(after));
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterAfter) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (before != null) {
            Specification<Player> filterBefore = (playerRoot, query, builder) ->
                    builder.lessThanOrEqualTo(playerRoot.get("birthday"), new Date(before));
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterBefore) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (banned != null) {
            Specification<Player> filterBanned = (playerRoot, query, builder) ->
                    builder.equal(playerRoot.get("banned"), banned);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterBanned) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (minExperience != null) {
            Specification<Player> filterMinExperience = (playerRoot, query, builder) ->
                    builder.ge(playerRoot.get("experience"), minExperience);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterMinExperience) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (maxExperience != null) {
            Specification<Player> filterMaxExperience = (playerRoot, query, builder) ->
                    builder.le(playerRoot.get("experience"), maxExperience);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterMaxExperience) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (minLevel != null) {
            Specification<Player> filterMinLevel = (playerRoot, query, builder) ->
                    builder.ge(playerRoot.get("level"), minLevel);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterMinLevel) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }
        if (maxLevel != null) {
            Specification<Player> filterMaxLevel = (playerRoot, query, builder) ->
                    builder.le(playerRoot.get("level"), maxLevel);
            resultSpecification = resultSpecification != null ? resultSpecification.and(filterMaxLevel) :
                    (playerRoot, query, builder) -> builder.isTrue(builder.literal(true));
        }

        return resultSpecification;
    }

    private Boolean isIdValid(Long id) {
        if (id == null) return false;
        return (id > 0);
    }

    private Boolean isNameValid(String name) {
        if (name == null || name.isEmpty()) return false;
        return (name.length() <= 12);
    }

    private Boolean isTitleValid(String title) {
        if (title == null) return false;
        return (title.length() <= 30);
    }

    private Boolean isDateValid(Date date) {
        if (date == null) return false;
        long dateMillis = date.getTime();
        long afterMillis = Date.valueOf("2000-01-01").getTime();
        long beforeMillis = Date.valueOf("3001-01-01").getTime();
        return (dateMillis >= afterMillis && dateMillis < beforeMillis);
    }

    private Boolean isExperienceValid (Integer experience) {
        if (experience == null) return false;
        return (experience >= 0 && experience <= 10000000);
    }

    private Integer calculateLevel(Integer experience) {
        return (int)((Math.sqrt(2500D + 200D * experience) - 50) / 100);
    }

    private Integer calculateUntilNextLevel(Integer experience, Integer level) {
        return 50 * (level + 1) * (level + 2) - experience;
    }
}
