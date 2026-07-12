package buno.model;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;

public class Host extends Jogador {
    private Baralho baralho;
    private ACL aclHost; //lista de Controle de acesso específica do Host no ZooKeeper

    public Host(String nome, Sala salaAtual) {
        super(nome, salaAtual);

        baralho = new Baralho();
        baralho.preencherBaralho();
        baralho.embaralhar();
        try {
            System.out.println("Tentando Criar o Host");
            //gera uma hash criptografada (Digest) usando o usuário "host" e a senha da sala
            String digest = DigestAuthenticationProvider.generateDigest("host:"+salaAtual.getSenha());
            //cria um objeto ACL dando permissão TOTAL (Perms.ALL) para quem tiver esse digest
            aclHost = new ACL(ZooDefs.Perms.ALL, new Id("digest", "host:"+salaAtual.getSenha()));
        } catch (Exception e) {
            System.out.println("EXPLODIU");
        }
    }

    // Usado na ELEIÇÃO DE LÍDER (tolerância a falhas): promove um Jogador comum já
    // existente a Host, sem recriar/reembaralhar o baralho — o baralho real já existe
    // no ZooKeeper (nó /baralho), possivelmente já parcialmente consumido pela partida
    // em andamento. Reaproveita path, conexão e mão do jogador que está sendo promovido.
    public Host(Jogador jogadorExistente) {
        super(jogadorExistente.getNome(), jogadorExistente.getSalaAtual());
        this.setPath(jogadorExistente.getPath());
        this.setConexaoPath(jogadorExistente.getConexaoPath());
        this.importMao(jogadorExistente.exportMao());
        this.setQuantidadeCartas(jogadorExistente.getQuantidadeCartas());
        this.setPenalidadeEmAndamento(jogadorExistente.isPenalidadeEmAndamento());

        try {
            System.out.println("Assumindo papel de Host (eleição de líder)");
            aclHost = new ACL(ZooDefs.Perms.ALL, new Id("digest", "host:" + jogadorExistente.getSalaAtual().getSenha()));
        } catch (Exception e) {
            System.out.println("Erro ao gerar ACL do novo host: " + e.getMessage());
        }
    }

    public ACL getAclHost() {
        return aclHost;
    }

    public Baralho getBaralho() {return baralho;}
}
