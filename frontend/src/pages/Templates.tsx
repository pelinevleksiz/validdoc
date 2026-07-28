import { useState } from "react"
import { Link } from "react-router"
import axios from "axios"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import templatesTitle from "@/assets/sablonlar-title.png"

interface TemplateSummary {
  id: number
  name: string
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function Templates() {
  const queryClient = useQueryClient()
  const [templatePendingDeactivation, setTemplatePendingDeactivation] = useState<TemplateSummary | null>(null)
  const [deactivateError, setDeactivateError] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["templates"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<TemplateSummary>>("/api/templates?page=0&size=50")
      return res.data
    },
  })

  const deactivateMutation = useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/api/templates/${id}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["templates"] })
      setTemplatePendingDeactivation(null)
      setDeactivateError(null)
    },
    onError: (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.data?.message) {
        setDeactivateError(error.response.data.message)
      } else {
        setDeactivateError("İşlem başarısız oldu.")
      }
    },
  })

  function handleOpenChange(open: boolean) {
    if (!open) {
      setTemplatePendingDeactivation(null)
      setDeactivateError(null)
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <img src={templatesTitle} alt="Şablonlar" className="h-[27px] w-auto" />
        <Button render={<Link to="/templates/new">Şablon ekle</Link>} />
      </div>

      {isLoading && <p className="text-muted-foreground">Yükleniyor...</p>}
      {isError && <p className="text-destructive">Şablonlar yüklenemedi.</p>}

      {data && data.content.length === 0 && (
        <p className="text-muted-foreground">Henüz şablon oluşturulmamış.</p>
      )}

      {data && data.content.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Şablon adı</TableHead>
              <TableHead className="text-right">İşlemler</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.content.map((template) => (
              <TableRow key={template.id}>
                <TableCell>{template.name}</TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    onClick={() => setTemplatePendingDeactivation(template)}
                  >
                    Devre dışı bırak
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <AlertDialog open={templatePendingDeactivation !== null} onOpenChange={handleOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {templatePendingDeactivation?.name} devre dışı bırakılsın mı?
            </AlertDialogTitle>
            <AlertDialogDescription>
              Bu şablon artık yeni belge yüklemelerinde seçilemeyecek. Bu şablonla daha önce işlenmiş belgeler etkilenmez.
            </AlertDialogDescription>
          </AlertDialogHeader>

          {deactivateError && (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {deactivateError}
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel>Vazgeç</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault()
                templatePendingDeactivation && deactivateMutation.mutate(templatePendingDeactivation.id)
              }}
              disabled={deactivateMutation.isPending}
            >
              {deactivateMutation.isPending ? "İşleniyor..." : "Devre dışı bırak"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

export default Templates