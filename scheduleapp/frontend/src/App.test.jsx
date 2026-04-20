import React from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import axios from 'axios'

vi.mock('axios')

const emptyGantt = {
  projectStart: null,
  projectEnd: null,
  totalDays: 0,
  bars: [],
}

function mockInitialLoads(operations = [], ganttData = emptyGantt, ganttLateData = emptyGantt) {
  axios.get.mockImplementation((url) => {
    if (url === '/api/operations') return Promise.resolve({ data: operations })
    if (url === '/api/operations/gantt') return Promise.resolve({ data: ganttData })
    if (url === '/api/operations/gantt-late') return Promise.resolve({ data: ganttLateData })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

const sampleOperation = {
  id: 1,
  uuid: '11111111-1111-1111-1111-111111111111',
  name: 'Cięcie',
  startTime: '2026-04-20T08:00:00',
  endTime: '2026-04-21T08:00:00',
  workerCount: 2,
  resources: 'Piła',
  totalCost: 1200,
  crashingCostPerDay: 100,
  maxCrashingDays: 1,
  crashedDays: 0,
  asap: false,
  predecessorIds: '',
  durationInDays: 1,
}

describe('App', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    window.confirm = vi.fn(() => true)
  })

  it('renders empty-state charts after loading without operations', async () => {
    mockInitialLoads()

    render(<App />)

    expect(screen.getByRole('heading', { name: 'Harmonogram Operacji' })).toBeInTheDocument()
    expect(await screen.findByText('Brak operacji w bazie danych.')).toBeInTheDocument()

    expect(screen.getAllByText('Brak operacji do wyświetlenia na wykresie.')).toHaveLength(2)
    expect(screen.getAllByText('Brak danych pracowników.')).toHaveLength(2)
  })

  it('renders operations fetched from the backend', async () => {
    mockInitialLoads([sampleOperation])

    render(<App />)

    expect((await screen.findAllByText('Cięcie')).length).toBeGreaterThan(0)
    expect(screen.getByText('Piła')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Usuń wszystkie' })).toBeInTheDocument()
  })

  it('switches the form into ASAP mode', async () => {
    const user = userEvent.setup()
    mockInitialLoads()

    render(<App />)

    await user.click(screen.getByRole('checkbox'))

    expect(screen.getByText('Czas trwania operacji:')).toBeInTheDocument()
    expect(screen.queryByText('Czas rozpoczęcia:')).not.toBeInTheDocument()
    expect(screen.queryByText('Czas zakończenia:')).not.toBeInTheDocument()
  })

  it('submits a standard operation and refreshes data', async () => {
    const user = userEvent.setup()
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: { id: 10 } })

    render(<App />)

    fireEvent.change(getInputAfterLabel((await screen.findAllByText('Nazwa operacji:'))[0]), {
      target: { value: 'Spawanie' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas rozpoczęcia:')), {
      target: { value: '2026-04-20T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas zakończenia:')), {
      target: { value: '2026-04-21T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '4' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Zasoby:')), {
      target: { value: 'Spawarka' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/operations', expect.objectContaining({
        name: 'Spawanie',
        asap: false,
        startTime: '2026-04-20T08:00',
        endTime: '2026-04-21T08:00',
        workerCount: 4,
        resources: 'Spawarka',
        predecessorIds: '',
      }))
    })

    expect(axios.get).toHaveBeenCalledWith('/api/operations')
    expect(axios.get).toHaveBeenCalledWith('/api/operations/gantt')
    expect(axios.get).toHaveBeenCalledWith('/api/operations/gantt-late')
  })

  // ============================================================
  // TESTY: Walidacja pola czasu trwania (krok 0.5h, min 0.5h)
  // ============================================================

  it('duration input in ASAP mode has min=0.5 and step=0.5', async () => {
    const user = userEvent.setup()
    mockInitialLoads()

    const { container } = render(<App />)

    await user.click(screen.getByRole('checkbox'))

    // Szukamy inputu liczbowego widocznego po zaznaczeniu ASAP
    const durationInput = container.querySelector('input[type="number"][step="0.5"]')
    expect(durationInput).not.toBeNull()
    expect(durationInput.min).toBe('0.5')
    expect(durationInput.step).toBe('0.5')
  })

  it('submits ASAP operation with 9 hours (previously rejected by wrong step)', async () => {
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: { id: 20, asap: true, asapDurationHours: 9.0 } })

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    // Przełącz na ASAP
    fireEvent.click(screen.getByRole('checkbox'))

    fireEvent.change((await screen.findAllByText('Nazwa operacji:'))[0].nextElementSibling, {
      target: { value: 'Test 9h' },
    })

    const durationInput = container.querySelector('input[type="number"][step="0.5"]')
    fireEvent.change(durationInput, { target: { value: '9' } })

    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '2' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/operations', expect.objectContaining({
        name: 'Test 9h',
        asap: true,
        asapDurationHours: 9.0,
      }))
    })
  })

  it('submits ASAP operation with 0.5 hours (minimum valid value)', async () => {
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: { id: 21, asap: true, asapDurationHours: 0.5 } })

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    fireEvent.click(screen.getByRole('checkbox'))

    fireEvent.change((await screen.findAllByText('Nazwa operacji:'))[0].nextElementSibling, {
      target: { value: 'Pół godziny' },
    })

    const durationInput = container.querySelector('input[type="number"][step="0.5"]')
    fireEvent.change(durationInput, { target: { value: '0.5' } })

    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '1' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/operations', expect.objectContaining({
        asap: true,
        asapDurationHours: 0.5,
      }))
    })
  })

  it('converts days to hours before submitting ASAP operation', async () => {
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: { id: 22, asap: true, asapDurationHours: 48 } })

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    fireEvent.click(screen.getByRole('checkbox'))

    fireEvent.change((await screen.findAllByText('Nazwa operacji:'))[0].nextElementSibling, {
      target: { value: '2 dni' },
    })

    const durationInput = container.querySelector('input[type="number"][step="0.5"]')
    fireEvent.change(durationInput, { target: { value: '2' } })

    // Zmień jednostkę na "dni"
    const unitSelect = container.querySelector('select')
    fireEvent.change(unitSelect, { target: { value: 'days' } })

    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '1' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/operations', expect.objectContaining({
        asap: true,
        asapDurationHours: 48,
      }))
    })
  })

  it('shows error when ASAP duration is zero', async () => {
    mockInitialLoads()

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    fireEvent.click(screen.getByRole('checkbox'))

    fireEvent.change((await screen.findAllByText('Nazwa operacji:'))[0].nextElementSibling, {
      target: { value: 'Zerowy czas' },
    })

    const durationInput = container.querySelector('input[type="number"][step="0.5"]')
    fireEvent.change(durationInput, { target: { value: '0' } })

    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '1' },
    })

    // Używamy fireEvent.submit bezpośrednio na formularzu, aby ominąć walidację HTML5
    // i sprawdzić, że handleSubmit wyświetla błąd JS dla wartości <= 0
    fireEvent.submit(container.querySelector('form'))

    expect(await screen.findByText('Czas trwania musi być większy od 0.')).toBeInTheDocument()
    expect(axios.post).not.toHaveBeenCalled()
  })

  // ============================================================
  // TESTY: Usuwanie operacji
  // ============================================================

  it('deletes a single operation when confirmed', async () => {
    mockInitialLoads([sampleOperation])
    axios.delete.mockResolvedValue({ data: 'OK' })
    window.confirm = vi.fn(() => true)

    render(<App />)
    await screen.findAllByText('Cięcie')

    fireEvent.click(screen.getByRole('button', { name: 'Usuń' }))

    await waitFor(() => {
      expect(axios.delete).toHaveBeenCalledWith('/api/operations/1')
    })
    expect(axios.get).toHaveBeenCalledWith('/api/operations')
  })

  it('does not delete when user cancels the single-delete confirmation', async () => {
    mockInitialLoads([sampleOperation])
    window.confirm = vi.fn(() => false)

    render(<App />)
    await screen.findAllByText('Cięcie')

    fireEvent.click(screen.getByRole('button', { name: 'Usuń' }))

    expect(axios.delete).not.toHaveBeenCalled()
  })

  it('deletes all operations when confirmed', async () => {
    mockInitialLoads([sampleOperation])
    axios.delete.mockResolvedValue({ data: 'OK' })
    window.confirm = vi.fn(() => true)

    render(<App />)
    await screen.findByRole('button', { name: 'Usuń wszystkie' })

    fireEvent.click(screen.getByRole('button', { name: 'Usuń wszystkie' }))

    await waitFor(() => {
      expect(axios.delete).toHaveBeenCalledWith('/api/operations')
    })
    expect(axios.get).toHaveBeenCalledWith('/api/operations')
  })

  it('does not delete all when user cancels the confirmation', async () => {
    mockInitialLoads([sampleOperation])
    window.confirm = vi.fn(() => false)

    render(<App />)
    await screen.findByRole('button', { name: 'Usuń wszystkie' })

    fireEvent.click(screen.getByRole('button', { name: 'Usuń wszystkie' }))

    expect(axios.delete).not.toHaveBeenCalled()
  })

  it('hides the "Usuń wszystkie" button when there are no operations', async () => {
    mockInitialLoads([])

    render(<App />)
    await screen.findByText('Brak operacji w bazie danych.')

    expect(screen.queryByRole('button', { name: 'Usuń wszystkie' })).not.toBeInTheDocument()
  })

  // ============================================================
  // TESTY: Eksport i import pliku
  // ============================================================

  it('export button sets window.location.href to the export endpoint', async () => {
    mockInitialLoads()

    // Zastąp window.location obiektem z możliwością odczytu href
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
      configurable: true,
    })

    render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    fireEvent.click(screen.getByRole('button', { name: 'Zapisz do pliku (JSON)' }))

    expect(window.location.href).toBe('/api/operations/export')
  })

  it('import button triggers file input click', async () => {
    const user = userEvent.setup()
    mockInitialLoads()

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    const fileInputs = container.querySelectorAll('input[type="file"]')
    const importInput = fileInputs[0]
    const clickSpy = vi.spyOn(importInput, 'click')

    await user.click(screen.getByRole('button', { name: 'Wczytaj z pliku (JSON)' }))

    expect(clickSpy).toHaveBeenCalled()
  })

  it('imports file after confirmation and refreshes data', async () => {
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: [{ id: 99, name: 'Importowana' }] })
    window.confirm = vi.fn(() => true)

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    const file = new File(['[{"name":"Importowana","workerCount":1}]'], 'import.json', {
      type: 'application/json',
    })
    const fileInputs = container.querySelectorAll('input[type="file"]')
    const importInput = fileInputs[0]

    fireEvent.change(importInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith(
        '/api/operations/import',
        expect.any(FormData),
      )
    })
    expect(axios.get).toHaveBeenCalledWith('/api/operations')
  })

  it('does not import when user cancels the confirmation dialog', async () => {
    mockInitialLoads()
    window.confirm = vi.fn(() => false)

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    const file = new File(['[]'], 'import.json', { type: 'application/json' })
    const importInput = container.querySelectorAll('input[type="file"]')[0]

    fireEvent.change(importInput, { target: { files: [file] } })

    expect(axios.post).not.toHaveBeenCalled()
  })

  it('merge button triggers the second file input click', async () => {
    const user = userEvent.setup()
    mockInitialLoads()

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    const fileInputs = container.querySelectorAll('input[type="file"]')
    const mergeInput = fileInputs[1]
    const clickSpy = vi.spyOn(mergeInput, 'click')

    await user.click(screen.getByRole('button', { name: 'Dołącz z pliku (JSON)' }))

    expect(clickSpy).toHaveBeenCalled()
  })

  it('merge file triggers import-merge endpoint and refreshes data', async () => {
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: [{ id: 100, name: 'Scalona' }] })

    const { container } = render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    const file = new File(['[{"name":"Scalona","workerCount":1}]'], 'merge.json', {
      type: 'application/json',
    })
    const mergeInput = container.querySelectorAll('input[type="file"]')[1]

    fireEvent.change(mergeInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith(
        '/api/operations/import-merge',
        expect.any(FormData),
      )
    })
    expect(axios.get).toHaveBeenCalledWith('/api/operations')
  })

  // ============================================================
  // TESTY: Wyświetlanie błędów backendu
  // ============================================================

  it('displays backend error message when POST fails', async () => {
    mockInitialLoads()
    axios.post.mockRejectedValue({
      response: { data: 'Czas zakończenia musi być późniejszy niż czas rozpoczęcia.' },
    })

    render(<App />)
    await screen.findAllByText('Nazwa operacji:')

    fireEvent.change(getInputAfterLabel((await screen.findAllByText('Nazwa operacji:'))[0]), {
      target: { value: 'Błędna' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas rozpoczęcia:')), {
      target: { value: '2026-04-21T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas zakończenia:')), {
      target: { value: '2026-04-20T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '1' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    expect(
      await screen.findByText('Czas zakończenia musi być późniejszy niż czas rozpoczęcia.'),
    ).toBeInTheDocument()
  })

  it('displays generic error when DELETE single operation fails', async () => {
    mockInitialLoads([sampleOperation])
    axios.delete.mockRejectedValue(new Error('Network error'))
    window.confirm = vi.fn(() => true)

    render(<App />)
    await screen.findAllByText('Cięcie')

    fireEvent.click(screen.getByRole('button', { name: 'Usuń' }))

    expect(await screen.findByText('Błąd usuwania operacji.')).toBeInTheDocument()
  })
})

function getInputAfterLabel(labelElement) {
  const input = labelElement.nextElementSibling
  if (!input) throw new Error(`Missing input for label: ${labelElement.textContent}`)
  return input
}
