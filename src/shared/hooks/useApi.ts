import { useCallback, useEffect, useState, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { api } from '../api';
import { useStore } from '../store';
export function useApi<T>(path: string | null, initial: T) {
  const inFlight = useRef(false),
    request = useRef(0);
  const { user } = useStore();
  const location = useLocation();
  const identity = `${user?.id || 'public'}:${user?.workspaceKey || ''}:${path}`;
  const [state, setState] = useState({
      identity,
      data: initial,
      loading: Boolean(path),
      error: '',
    }),
    [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((n) => n + 1), []);
  useEffect(() => {
    if (!path) {
      setState({ identity, data: initial, loading: false, error: '' });
      return;
    }
    const controller = new AbortController();
    const requestId = ++request.current;
    inFlight.current = true;
    setState((old) => ({
      identity,
      data: old.identity === identity ? old.data : initial,
      loading: old.identity !== identity || old.loading,
      error: '',
    }));
    api<T>(path, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted)
          setState({
            identity,
            data,
            loading: false,
            error: navigator.onLine
              ? ''
              : 'Dữ liệu đã lưu trên thiết bị; kết nối mạng để cập nhật.',
          });
      })
      .catch((e: Error) => {
        if (!controller.signal.aborted)
          setState((old) => ({ ...old, identity, loading: false, error: e.message }));
      })
      .finally(() => {
        if (request.current === requestId) inFlight.current = false;
      });
    return () => controller.abort();
  }, [path, identity, revision, location.pathname, location.search]);
  useEffect(() => {
    if (!path) return;
    const update = () => {
      if (!inFlight.current && navigator.onLine && document.visibilityState === 'visible') reload();
    };
    window.addEventListener('focus', update);
    window.addEventListener('synera:data-changed', update);
    const timer = setInterval(
      update,
      /^\/(notifications|tasks|sales-orders|sales-replacements)(\/|\?|$)/.test(path) ? 2000 : 20000,
    );
    return () => {
      window.removeEventListener('focus', update);
      window.removeEventListener('synera:data-changed', update);
      clearInterval(timer);
    };
  }, [path, reload]);
  const current =
    state.identity === identity
      ? state
      : { identity, data: initial, loading: Boolean(path), error: '' };
  return { ...current, reload, setData: (data: T) => setState((old) => ({ ...old, data })) };
}
