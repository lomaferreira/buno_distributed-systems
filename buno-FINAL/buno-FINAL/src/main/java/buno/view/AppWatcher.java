package buno.view;

import buno.controller.ZookeeperService;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class AppWatcher implements Watcher {
    private final ZookeeperService conexao;
    private final String caminhoConexoes;

    public AppWatcher(ZookeeperService conexao, String salaPath){
        this.conexao = conexao;
        //colocar o caminho dos nós efêmeros
        this.caminhoConexoes = salaPath + "/conexoes";

    }

    @Override
    public void process(WatchedEvent event) {
        //verfica se houve mudança nos nós filhos em /conexoes (se alguem entrou ou saiu)
        if(event.getType() == Event.EventType.NodeChildrenChanged && event.getPath().equals(caminhoConexoes)){
            atualizarJogadoresNaTela();
        }

    }

    public void atualizarJogadoresNaTela(){
        try {
            ZooKeeper zk = conexao.getZookeeper(); //conexao ativa

            // Puxa a lista de conexões ativas e REARMA o watcher passando 'this'
            List<String> conexoesAtivas = zk.getChildren(caminhoConexoes, this);

            System.out.println(App.VERDE + "Total de jogadores ativos na sala agora: " + conexoesAtivas.size() + App.RESET);

        }catch (Exception e){
            System.err.println("Erro ao atualizar o monitor de conexões: " + e.getMessage());
        }
    }
}
