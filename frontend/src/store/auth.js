import {create} from 'zustand';

/**
 * null -- ещё не знаем
 * true -- авторизован
 * false -- не авторизован
 */
const useAuthStore = create((set) => ({
    isAuthed: null,
    setIsAuthed: (isAuthed) => set({isAuthed}),
}));

export default useAuthStore;