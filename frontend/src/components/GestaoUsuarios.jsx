// src/components/GestaoUsuarios.jsx
import React, { useCallback, useEffect, useState } from 'react';
import { atualizarUsuario, criarUsuario, excluirUsuario, listarUsuarios } from '../services/api';

const PERFIS = {
  ADMINISTRADOR: 'Administrador',
  OPERADOR: 'Operador',
  VISUALIZADOR: 'Visualizador',
};

const FORM_VAZIO = { nome: '', email: '', perfil: 'OPERADOR' };

function GestaoUsuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [form, setForm] = useState(FORM_VAZIO);
  const [editandoId, setEditandoId] = useState(null);
  const [carregando, setCarregando] = useState(true);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState('');
  const [feedback, setFeedback] = useState('');

  const carregar = useCallback(async () => {
    try {
      setUsuarios(await listarUsuarios());
      setErro('');
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const avisar = (mensagem) => {
    setFeedback(mensagem);
    setTimeout(() => setFeedback(''), 3000);
  };

  const limparForm = () => {
    setForm(FORM_VAZIO);
    setEditandoId(null);
  };

  const salvar = async (evento) => {
    evento.preventDefault();
    setSalvando(true);
    try {
      if (editandoId) {
        await atualizarUsuario(editandoId, form);
        avisar('Usuário atualizado.');
      } else {
        await criarUsuario(form);
        avisar('Usuário cadastrado.');
      }
      limparForm();
      setErro('');
      await carregar();
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando(false);
    }
  };

  const editar = (usuario) => {
    setEditandoId(usuario.id);
    setForm({ nome: usuario.nome, email: usuario.email, perfil: usuario.perfil });
  };

  const alternarAtivo = async (usuario) => {
    try {
      await atualizarUsuario(usuario.id, {
        nome: usuario.nome,
        email: usuario.email,
        perfil: usuario.perfil,
        ativo: !usuario.ativo,
      });
      await carregar();
    } catch (e) {
      setErro(e.message);
    }
  };

  const remover = async (usuario) => {
    if (!window.confirm(`Excluir o usuário ${usuario.nome}?`)) return;
    try {
      await excluirUsuario(usuario.id);
      if (editandoId === usuario.id) limparForm();
      avisar('Usuário excluído.');
      await carregar();
    } catch (e) {
      setErro(e.message);
    }
  };

  return (
    <div className="row g-3">
      <div className="col-lg-4">
        <form className="surface-card p-4" onSubmit={salvar}>
          <h6 className="mb-1 fw-semibold text-theme-primary">
            {editandoId ? 'Editar usuário' : 'Novo usuário'}
          </h6>
          <small className="text-theme-secondary d-block mb-3" style={{ fontSize: '0.8rem' }}>
            Operadores tratam ocorrências; administradores também gerenciam usuários.
          </small>

          <div className="mb-3">
            <label htmlFor="usuario-nome" className="form-label small text-theme-secondary">Nome</label>
            <input
              id="usuario-nome"
              className="form-control"
              required
              maxLength={100}
              value={form.nome}
              onChange={(e) => setForm({ ...form, nome: e.target.value })}
            />
          </div>
          <div className="mb-3">
            <label htmlFor="usuario-email" className="form-label small text-theme-secondary">E-mail</label>
            <input
              id="usuario-email"
              type="email"
              className="form-control"
              required
              maxLength={150}
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </div>
          <div className="mb-3">
            <label htmlFor="usuario-perfil" className="form-label small text-theme-secondary">Perfil</label>
            <select
              id="usuario-perfil"
              className="form-select"
              value={form.perfil}
              onChange={(e) => setForm({ ...form, perfil: e.target.value })}
            >
              {Object.entries(PERFIS).map(([valor, rotulo]) => (
                <option key={valor} value={valor}>{rotulo}</option>
              ))}
            </select>
          </div>

          <div className="d-flex gap-2">
            <button type="submit" className="btn btn-primary btn-sm fw-medium px-3" disabled={salvando}>
              {salvando ? 'Salvando...' : editandoId ? 'Salvar alterações' : 'Cadastrar'}
            </button>
            {editandoId && (
              <button type="button" className="theme-toggle-btn px-3" onClick={limparForm}>
                Cancelar
              </button>
            )}
          </div>
        </form>
      </div>

      <div className="col-lg-8">
        <div className="surface-card overflow-hidden h-100">
          <div className="p-4 pb-3">
            <h6 className="mb-0 fw-semibold text-theme-primary">Usuários da plataforma</h6>
            <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
              {usuarios.length} cadastrado(s)
            </small>
          </div>

          {(erro || feedback) && (
            <div className="px-4 pb-3">
              <div
                className={`alert ${erro ? 'alert-danger' : 'alert-success'} py-2 px-3 mb-0`}
                style={{ fontSize: '0.85rem' }}
              >
                {erro || feedback}
              </div>
            </div>
          )}

          {carregando ? (
            <div className="text-theme-secondary py-5 text-center">Carregando usuários...</div>
          ) : usuarios.length === 0 ? (
            <div className="text-theme-secondary py-5 text-center">Nenhum usuário cadastrado.</div>
          ) : (
            <div className="table-responsive">
              <table className="table table-clean">
                <thead>
                  <tr>
                    <th>Nome</th>
                    <th>E-mail</th>
                    <th>Perfil</th>
                    <th>Situação</th>
                    <th className="text-end">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {usuarios.map((usuario) => (
                    <tr key={usuario.id}>
                      <td className="fw-medium">{usuario.nome}</td>
                      <td className="text-theme-secondary">{usuario.email}</td>
                      <td>{PERFIS[usuario.perfil] || usuario.perfil}</td>
                      <td>
                        <span
                          className={`badge fw-medium ${
                            usuario.ativo ? 'bg-success-subtle text-success' : 'bg-secondary-subtle text-secondary'
                          }`}
                        >
                          {usuario.ativo ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="text-end text-nowrap">
                        <button type="button" className="btn btn-link btn-sm p-0 me-3" onClick={() => editar(usuario)}>
                          Editar
                        </button>
                        <button type="button" className="btn btn-link btn-sm p-0 me-3" onClick={() => alternarAtivo(usuario)}>
                          {usuario.ativo ? 'Desativar' : 'Ativar'}
                        </button>
                        <button type="button" className="btn btn-link btn-sm p-0 text-danger" onClick={() => remover(usuario)}>
                          Excluir
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default GestaoUsuarios;
