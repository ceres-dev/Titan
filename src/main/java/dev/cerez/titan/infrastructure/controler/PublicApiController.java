package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.TitanApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/xapi")
@RequiredArgsConstructor
public class PublicApiController {

    public final TitanApplication titan;

    private final HashMap<String, Municipio> municipios = new HashMap<>();

    @GetMapping("/get")
    public ResponseEntity<Municipio> get(@RequestParam String name) {
        if (!municipios.containsKey(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(municipios.get(name), HttpStatus.OK);
    }

    @PutMapping("/table")
    public ResponseEntity<Void> add(@RequestParam String name, @RequestBody int population, @RequestParam double temperature) {
        municipios.put(name, new Municipio(name, population, temperature));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/table")
    public ResponseEntity<Void> delete(@RequestParam String name) {
        municipios.remove(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/table")
    public ResponseEntity<Void> update(@RequestParam String name, @RequestBody int population, @RequestParam double temperature) {
        if (!municipios.containsKey(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        municipios.put(name, new Municipio(name, population, temperature));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/table")
    public ResponseEntity<List<Municipio>> getAll() {
        return new ResponseEntity<>(municipios.values().stream().toList(), HttpStatus.OK);
    }

    public record Municipio(String name, int population, double temperature) {}
}
