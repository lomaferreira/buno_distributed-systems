package buno.model;
import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Stack;
import java.util.Collections;

public class Baralho {
    private Stack<Carta> cartas;

    //ler o arquivo csv e adicona na minha pilha de cartas
    public void preencherBaralho(){
        cartas = new Stack<>();
        try (var input = getClass().getResourceAsStream("/buno/data/cartas_disponiveis.csv")) {
            if (input == null) {
                throw new IOException("cartas_disponiveis.csv não encontrado no classpath");
            }
            List<String> linhas = new String(input.readAllBytes()).lines().toList();
            for (String linha : linhas) {
                if (linha.isBlank()) continue;
                cartas.add(new Carta(linha));
            }
        } catch (IOException e) {
            System.out.println("Erro ao carregar o baralho: " + e.getMessage());
        }
        embaralhar();

    }

    public void embaralhar(){
        Collections.shuffle(cartas);
    }

    //retorna a carta que está no topo do baralho
    public Carta comprar(){
        return cartas.pop();
    }

    public boolean isEmpty(){
        return cartas.isEmpty();
    }

}
