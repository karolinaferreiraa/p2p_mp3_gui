import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class P2PJavaGUI extends JFrame {

    // ==========================================
    // CONFIGURAÇÕES
    // ==========================================
    private static final String PASTA_COMPARTILHADOS = "compartilhados";
    private static final String PASTA_RECEBIDOS = "recebidos";
    private static final int BUFFER = 4096;

    private JTextField txtHostServer, txtPortServer, txtHostClient, txtPortClient;
    private JTextArea txtLog;
    private DefaultListModel<String> listModel;
    private JList<String> listArquivos;
    private JProgressBar progressBar;
    private JLabel lblProgresso;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader entrada;
    private PrintWriter saida;
    private InputStream inputStreamCliente;

    public P2PJavaGUI() {
        super("P2P MP3 - Java GUI");

        new File(PASTA_COMPARTILHADOS).mkdirs();
        new File(PASTA_RECEBIDOS).mkdirs();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topo = new JPanel(new GridLayout(2, 1));

        JPanel painelServidor = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelServidor.setBorder(BorderFactory.createTitledBorder("Servidor"));
        txtHostServer = new JTextField("0.0.0.0", 10);
        txtPortServer = new JTextField("5000", 6);
        JButton btnServidor = new JButton("Iniciar Servidor");
        painelServidor.add(new JLabel("Host:"));
        painelServidor.add(txtHostServer);
        painelServidor.add(new JLabel("Porta:"));
        painelServidor.add(txtPortServer);
        painelServidor.add(btnServidor);

        JPanel painelCliente = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelCliente.setBorder(BorderFactory.createTitledBorder("Cliente"));
        txtHostClient = new JTextField("127.0.0.1", 10);
        txtPortClient = new JTextField("5000", 6);
        JButton btnConectar = new JButton("Conectar");
        JButton btnListar = new JButton("Listar MP3");
        JButton btnBaixar = new JButton("Baixar Selecionado");
        painelCliente.add(new JLabel("IP:"));
        painelCliente.add(txtHostClient);
        painelCliente.add(new JLabel("Porta:"));
        painelCliente.add(txtPortClient);
        painelCliente.add(btnConectar);
        painelCliente.add(btnListar);
        painelCliente.add(btnBaixar);

        topo.add(painelServidor);
        topo.add(painelCliente);
        add(topo, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        listArquivos = new JList<>(listModel);
        JScrollPane scrollLista = new JScrollPane(listArquivos);
        scrollLista.setBorder(BorderFactory.createTitledBorder("Arquivos Remotos"));

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollLista, scrollLog);
        split.setDividerLocation(200);
        add(split, BorderLayout.CENTER);

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setBorder(BorderFactory.createTitledBorder("Progresso"));
        progressBar = new JProgressBar(0, 100);
        lblProgresso = new JLabel("Aguardando...");
        rodape.add(progressBar, BorderLayout.CENTER);
        rodape.add(lblProgresso, BorderLayout.SOUTH);
        add(rodape, BorderLayout.SOUTH);

        btnServidor.addActionListener(e -> iniciarServidor());
        btnConectar.addActionListener(e -> conectarPeer());
        btnListar.addActionListener(e -> listarArquivosRemotos());
        btnBaixar.addActionListener(e -> baixarArquivoSelecionado());
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // ------------------------------------------
    // SERVIDOR
    // ------------------------------------------
    private void iniciarServidor() {
        String host = txtHostServer.getText().trim(); // apenas informativo
        int porta = Integer.parseInt(txtPortServer.getText().trim());

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(porta);
                log("[SERVIDOR] Ouvindo em " + host + ":" + porta);

                while (true) {
                    Socket socket = serverSocket.accept();
                    log("[NOVA CONEXÃO] " + socket.getInetAddress());
                    new Thread(() -> tratarCliente(socket)).start();
                }
            } catch (Exception ex) {
                log("[ERRO SERVIDOR] " + ex.getMessage());
            }
        }).start();
    }

    private List<String> listarMp3Locais() {
        List<String> arquivos = new ArrayList<>();
        File pasta = new File(PASTA_COMPARTILHADOS);
        File[] lista = pasta.listFiles((dir, nome) -> nome.toLowerCase().endsWith(".mp3"));
        if (lista != null) {
            for (File f : lista) {
                arquivos.add(f.getName());
            }
        }
        return arquivos;
    }

    private void tratarCliente(Socket socket) {
        try {
            BufferedReader entradaLocal = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter saidaLocal = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                String comando = entradaLocal.readLine();
                if (comando == null) break;

                log("[COMANDO RECEBIDO] " + comando);

                if (comando.equals("LIST")) {
                    List<String> arquivos = listarMp3Locais();
                    if (arquivos.isEmpty()) {
                        saidaLocal.println("NENHUM_ARQUIVO");
                    } else {
                        for (String nome : arquivos) {
                            saidaLocal.println(nome);
                        }
                        saidaLocal.println("FIM_LISTA");
                    }
                } else if (comando.startsWith("GET ")) {
                    String nome = comando.substring(4).trim();
                    File arquivo = new File(PASTA_COMPARTILHADOS, nome);

                    if (arquivo.exists()) {
                        long tamanho = arquivo.length();
                        saidaLocal.println("OK " + tamanho);

                        String confirm = entradaLocal.readLine();
                        if ("READY".equals(confirm)) {
                            FileInputStream fis = new FileInputStream(arquivo);
                            OutputStream os = socket.getOutputStream();
                            byte[] buffer = new byte[BUFFER];
                            int bytesLidos;

                            while ((bytesLidos = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesLidos);
                            }
                            os.flush();
                            fis.close();
                            log("[ARQUIVO ENVIADO] " + nome);
                        }
                    } else {
                        saidaLocal.println("ERRO Arquivo não encontrado");
                    }
                } else if (comando.equals("SAIR")) {
                    break;
                } else {
                    saidaLocal.println("ERRO Comando inválido");
                }
            }
            socket.close();
        } catch (Exception ex) {
            log("[ERRO CLIENTE] " + ex.getMessage());
        }
    }

    // ------------------------------------------
    // CLIENTE
    // ------------------------------------------
    private void conectarPeer() {
        String host = txtHostClient.getText().trim();
        int porta = Integer.parseInt(txtPortClient.getText().trim());

        new Thread(() -> {
            try {
                clientSocket = new Socket(host, porta);
                entrada = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                saida = new PrintWriter(clientSocket.getOutputStream(), true);
                inputStreamCliente = clientSocket.getInputStream();
                log("[CONECTADO] ao peer " + host + ":" + porta);
                JOptionPane.showMessageDialog(this, "Conectado com sucesso.");
            } catch (Exception ex) {
                log("[ERRO CONEXÃO] " + ex.getMessage());
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void listarArquivosRemotos() {
        if (saida == null || entrada == null) {
            JOptionPane.showMessageDialog(this, "Conecte primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                saida.println("LIST");
                SwingUtilities.invokeLater(() -> listModel.clear());

                while (true) {
                    String linha = entrada.readLine();
                    if (linha == null) break;
                    if (linha.equals("NENHUM_ARQUIVO")) {
                        log("[LISTA] Nenhum arquivo remoto encontrado.");
                        break;
                    }
                    if (linha.equals("FIM_LISTA")) {
                        break;
                    }
                    String item = linha;
                    SwingUtilities.invokeLater(() -> listModel.addElement(item));
                }

                log("[LISTA] Arquivos remotos atualizados.");
            } catch (Exception ex) {
                log("[ERRO LISTA] " + ex.getMessage());
            }
        }).start();
    }

    private void baixarArquivoSelecionado() {
        String nome = listArquivos.getSelectedValue();
        if (nome == null) {
            JOptionPane.showMessageDialog(this, "Selecione um arquivo.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (saida == null || entrada == null || inputStreamCliente == null) {
            JOptionPane.showMessageDialog(this, "Conecte primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                saida.println("GET " + nome);
                String resposta = entrada.readLine();

                if (resposta == null || !resposta.startsWith("OK")) {
                    log("[ERRO DOWNLOAD] " + resposta);
                    JOptionPane.showMessageDialog(this, resposta, "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                long tamanho = Long.parseLong(resposta.split(" ")[1]);
                saida.println("READY");

                File destino = new File(PASTA_RECEBIDOS, nome);
                FileOutputStream fos = new FileOutputStream(destino);

                byte[] buffer = new byte[BUFFER];
                long recebido = 0;

                while (recebido < tamanho) {
                    int bytesLidos = inputStreamCliente.read(buffer);
                    if (bytesLidos == -1) break;

                    fos.write(buffer, 0, bytesLidos);
                    recebido += bytesLidos;

                    int pct = tamanho > 0 ? (int) ((recebido * 100) / tamanho) : 0;
                    long finalRecebido = recebido;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(pct);
                        lblProgresso.setText("Baixando... " + pct + "% (" + finalRecebido + "/" + tamanho + " bytes)");
                    });
                }

                fos.close();
                log("[DOWNLOAD CONCLUÍDO] " + destino.getPath());
                JOptionPane.showMessageDialog(this, "Arquivo salvo em:\n" + destino.getPath());
            } catch (Exception ex) {
                log("[ERRO DOWNLOAD] " + ex.getMessage());
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new P2PJavaGUI().setVisible(true));
    }
}
