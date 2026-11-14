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

    @GetMapping("/get")
    public ResponseEntity<Municipio> get(@RequestParam String name) {
        if (!municipios.containsKey(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Log.info("GET XAPI");
        return new ResponseEntity<>(municipios.get(name), HttpStatus.OK);
    }

    @PutMapping("/table")
    public ResponseEntity<Void> add(@RequestBody Municipio municipio) {
        municipios.put(municipio.name, municipio);
        Log.info("PUT XAPI");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/table")
    public ResponseEntity<Void> delete(@RequestParam String name) {
        municipios.remove(name);
        Log.info("DELETE XAPI");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/table")
    public ResponseEntity<Void> update(@RequestBody Municipio municipio) {
        if (!municipios.containsKey(municipio.name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Log.info("UPDATE XAPI");
        municipios.put(municipio.name, municipio);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/table")
    public ResponseEntity<List<Municipio>> getAll() {
        Log.info("GET ALL XAPI");
        return new ResponseEntity<>(municipios.values().stream().toList(), HttpStatus.OK);
    }

    public record Municipio(String name, int population, double temperature) {}
}
