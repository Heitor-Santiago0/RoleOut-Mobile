# Abertura e ícone RolêOut

A raiz do Expo Router monta `StartupSplash` uma vez por inicialização. As telas e a restauração da sessão carregam por baixo da abertura. Ao terminar o vídeo local de 2,8 segundos, a sobreposição desaparece suavemente. Navegar ou voltar do segundo plano não reinicia a abertura.

O vídeo aprovado está em `assets/roleout-splash.mp4`. A abertura não depende da rede, não reproduz áudio e não mostra controles. A preferência de reduzir movimento pula a animação. Erros de reprodução ou um limite de 5,5 segundos liberam a interface, para a abertura não bloquear o app.

`app.json` configura a splash nativa azul-noite e os ícones: `icon.png` para iOS/ícone geral, camadas adaptativas e monocromática para Android, e `favicon.png` para web. O desenho simplificado usa somente R e marcador, sem o nome completo ou neon.

## Teste no dispositivo

Os novos módulos nativos (`expo-video` e `expo-splash-screen`) e os ícones exigem uma nova build do aplicativo. Reiniciar apenas o Metro não atualiza um app já instalado. Use o fluxo EAS existente no projeto. Valide a splash nativa em uma build de distribuição, pois Expo Go e development builds não reproduzem integralmente a abertura do sistema.

Confira: iniciar com o app fechado; entrar normalmente após a animação; abrir sem internet; navegar e voltar do segundo plano sem repetir a abertura; ativar reduzir movimento; testar ícones Android circulares/quadrados e temáticos; verificar o ícone iOS.
