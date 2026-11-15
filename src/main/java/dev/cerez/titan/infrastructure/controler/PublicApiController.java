package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.log.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/* Temporal */
@RestController
@RequestMapping("/xapi")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PublicApiController {

    public final TitanApplication titan;

    private final HashMap<String, Municipio> municipios = new HashMap<>();

    @GetMapping("/table/get")
    public ResponseEntity<Municipio> getTable(@RequestParam String name) {
        if (!municipios.containsKey(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(municipios.get(name), HttpStatus.OK);
    }

    @PutMapping("/table")
    public ResponseEntity<Void> addTable(@RequestBody Municipio municipio) {
        municipios.put(municipio.name, municipio);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/table")
    public ResponseEntity<Void> deleteTable(@RequestParam String name) {
        municipios.remove(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/table")
    public ResponseEntity<Void> updateTable(@RequestBody Municipio municipio) {
        if (!municipios.containsKey(municipio.name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        municipios.put(municipio.name, municipio);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/table")
    public ResponseEntity<List<Municipio>> getAllTable() {
        return new ResponseEntity<>(municipios.values().stream().toList(), HttpStatus.OK);
    }

    private final HashMap<String, User> users = new HashMap<>();

    @GetMapping("/user/get")
    public ResponseEntity<Municipio> getuser(@RequestParam String name) {
        if (!users.containsKey(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(municipios.get(name), HttpStatus.OK);
    }

    @PutMapping("/user")
    public ResponseEntity<Void> addUser(@RequestBody User municipio) {
        users.put(municipio.username, municipio);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user")
    public ResponseEntity<Void> deleteUser(@RequestParam String name) {
        users.remove(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user")
    public ResponseEntity<Void> updateUser(@RequestBody User user) {
        if (!users.containsKey(user.username)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        users.put(user.username, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user")
    public ResponseEntity<List<User>> getAllUser() {
        return new ResponseEntity<>(users.values().stream().toList(), HttpStatus.OK);
    }

    public record Municipio(String name, int population, double temperature) {}

    public record User(String username, String password, String email) {}
}
