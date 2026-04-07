P2P MP3 - PACOTE COM GUI

Conteúdo deste pacote:
- p2p_python_gui.py        -> versão Python com interface gráfica Tkinter
- P2PJavaGUI.java          -> versão Java com interface gráfica Swing
- compartilhados/          -> coloque aqui seus arquivos .mp3
- recebidos/               -> downloads salvos aqui

==================================================
1) VERSÃO PYTHON GUI
==================================================
Requisitos:
- Python 3
- Tkinter (normalmente já vem com Python)

Executar:
python p2p_python_gui.py

Como testar:
- Abra a aplicação em um terminal e clique em "Iniciar Servidor"
- Abra outra instância da aplicação e clique em "Conectar"
- Use 127.0.0.1 e a mesma porta, por exemplo 5000
- Clique em "Listar MP3"
- Selecione um arquivo e clique em "Baixar Selecionado"

==================================================
2) VERSÃO JAVA GUI
==================================================
Requisitos:
- JDK instalado

Compilar:
javac P2PJavaGUI.java

Executar:
java P2PJavaGUI

Teste local:
- Abra duas instâncias do programa
- Em uma, inicie o servidor
- Na outra, conecte em 127.0.0.1 na mesma porta

==================================================
3) OBSERVAÇÕES DIDÁTICAS
==================================================
Este exercício demonstra:
- arquitetura peer-to-peer
- comunicação por sockets TCP
- concorrência com threads
- troca de mensagens entre nós
- compartilhamento distribuído de arquivos

Os códigos estão comentados para uso em aula.
