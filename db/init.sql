-- =============================================================
-- Inicialização do MySQL do MetalTech
-- -------------------------------------------------------------
-- Executado automaticamente pela imagem oficial do MySQL somente na
-- PRIMEIRA inicialização, ou seja, com o volume mysql_data vazio
-- (montado em /docker-entrypoint-initdb.d/ pelo compose.yaml).
--
-- O banco "metaltech" e o usuário da aplicação (MYSQL_USER/MYSQL_PASSWORD)
-- já são criados pela própria imagem, que dá ao usuário acesso total ao banco.
--
-- As TABELAS (leituras_sensores, alertas_nivel e usuarios) NÃO são criadas
-- aqui: o back-end aplica as migrações do Flyway ao subir
-- (backend/src/main/resources/db/migration/V1..V3) e o Hibernate valida o
-- esquema. Assim o esquema tem uma única fonte e evolui por versão.
-- =============================================================

-- Acentuação correta (ç, ã, é) nas mensagens de alerta e nos nomes de usuários
ALTER DATABASE metaltech
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- O fuso do servidor fica em UTC (--default-time-zone no compose.yaml), igual
-- ao hibernate.jdbc.time_zone do back-end: os timestamps de telemetria são
-- gravados e lidos sem deslocamento.
